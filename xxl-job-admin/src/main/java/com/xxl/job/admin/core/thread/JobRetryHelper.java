package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.cron.CronExpression;
import com.xxl.job.admin.core.dto.RetryDto;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobLogDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 *
 */
public class JobRetryHelper {
	private static Logger logger = LoggerFactory.getLogger(JobRetryHelper.class);

	private static JobRetryHelper instance = new JobRetryHelper();
	public static JobRetryHelper getInstance(){
		return instance;
	}

	public static final long PRE_READ_MS = 5000;    // pre read

	private Thread scheduleThread;
	private Thread ringThread;
	private volatile boolean retryThreadToStop = false;
	private volatile boolean ringThreadToStop = false;
	private volatile static Map<Integer, List<RetryDto>> ringData = new ConcurrentHashMap<>();
	private static final String retryMsg = "<br><br><span style=\"color:#F39C12;\" > >>>>>>>>>>>"+ I18nUtil.getString("jobconf_trigger_type_retry") +"<<<<<<<<<<< </span><br>";
	public void start(){

		// schedule thread
		scheduleThread = new Thread(new Runnable() {
			@Override
			public void run() {

				try {
					TimeUnit.MILLISECONDS.sleep(5000 - System.currentTimeMillis()%1000 );
				} catch (InterruptedException e) {
					if (!retryThreadToStop) {
						logger.error(e.getMessage(), e);
					}
				}
				logger.info(">>>>>>>>> init xxl-job admin retry thred success.");

				// pre-read count: treadpool-size * trigger-qps (each trigger cost 50ms, qps = 1000/50 = 20)
				int preReadCount = (XxlJobAdminConfig.getAdminConfig().getTriggerPoolFastMax() + XxlJobAdminConfig.getAdminConfig().getTriggerPoolSlowMax()) * 20;

				while (!retryThreadToStop) {

					// Scan Job
					long start = System.currentTimeMillis();

					Connection conn = null;
					Boolean connAutoCommit = null;
					PreparedStatement preparedStatement = null;

					try {

						conn = XxlJobAdminConfig.getAdminConfig().getDataSource().getConnection();
						connAutoCommit = conn.getAutoCommit();
						conn.setAutoCommit(false);

						preparedStatement = conn.prepareStatement(  "select * from xxl_job_lock where lock_name = 'retry_lock' for update" );
						preparedStatement.execute();

						// 1、pre read
						long nowTime = System.currentTimeMillis();
						XxlJobLogDao xxlJobLogDao = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao();
						List<XxlJobLog> scheduleList = xxlJobLogDao.findRetryJobLog(nowTime + PRE_READ_MS, preReadCount);
						if (scheduleList!=null && scheduleList.size()>0) {
							// tx start
							nowTime = System.currentTimeMillis();
							// 2、push time-ring
							for (XxlJobLog jobLog: scheduleList) {
								// time-ring jump
								if (nowTime > jobLog.getNextRetryTime() + PRE_READ_MS) {
									xxlJobLogDao.updateHandleMsg(jobLog.getId()
											, ">>>>>>>>>>> xxl-job, schedule misfire, Skip"
											,2);
								} else if (nowTime > jobLog.getNextRetryTime()) {
									// 2.2、trigger-expire < 5s：direct-trigger && make next-trigger-time

									// 1、trigger
									JobTriggerPoolHelper.trigger(jobLog.getJobId()
											, TriggerTypeEnum.RETRY
											, (jobLog.getExecutorFailRetryCount()-1)
											, jobLog.getExecutorShardingParam()
											, jobLog.getExecutorParam()
											, null);
									logger.debug(">>>>>>>>>>> xxl-job, schedule push trigger : jobId = {}, jobLogId = {}", jobLog.getJobId(), jobLog.getId());
									xxlJobLogDao.updateHandleMsg(jobLog.getId(), retryMsg,1);

								} else {
									// 2.3、trigger-pre-read：time-ring trigger && make next-trigger-time

									// 1、make ring second
									int ringSecond = (int)((jobLog.getNextRetryTime()/1000)%60);

									// 2、push time ring
									pushTimeRing(ringSecond, jobLog);

									// 3、fresh next
									xxlJobLogDao.updateHandleMsg(jobLog.getId(), retryMsg,1);
								}

							}
						}

						// tx stop
					} catch (Exception e) {
						if (!retryThreadToStop) {
							logger.error(">>>>>>>>>>> xxl-job, JobRetryHelper#scheduleThread error:{}", e);
						}
					} finally {

						// commit
						if (conn != null) {
							try {
								conn.commit();
							} catch (SQLException e) {
								if (!retryThreadToStop) {
									logger.error(e.getMessage(), e);
								}
							}
							try {
								conn.setAutoCommit(connAutoCommit);
							} catch (SQLException e) {
								if (!retryThreadToStop) {
									logger.error(e.getMessage(), e);
								}
							}
							try {
								conn.close();
							} catch (SQLException e) {
								if (!retryThreadToStop) {
									logger.error(e.getMessage(), e);
								}
							}
						}

						// close PreparedStatement
						if (null != preparedStatement) {
							try {
								preparedStatement.close();
							} catch (SQLException e) {
								if (!retryThreadToStop) {
									logger.error(e.getMessage(), e);
								}
							}
						}
					}
					long cost = System.currentTimeMillis()-start;


					// Wait seconds, align second
					if (cost < 1000) {  // scan-overtime, not wait
						try {
							// pre-read period: success > scan each second; fail > skip this period;
							TimeUnit.MILLISECONDS.sleep((1000) - System.currentTimeMillis()%1000);
						} catch (InterruptedException e) {
							if (!retryThreadToStop) {
								logger.error(e.getMessage(), e);
							}
						}
					}

				}

				logger.info(">>>>>>>>>>> xxl-job, JobRetryHelper#scheduleThread stop");
			}
		});
		scheduleThread.setDaemon(true);
		scheduleThread.setName("xxl-job, admin JobRetryHelper#scheduleThread");
		scheduleThread.start();


		// ring thread
		ringThread = new Thread(new Runnable() {
			@Override
			public void run() {

				while (!ringThreadToStop) {

					// align second
					try {
						TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
					} catch (InterruptedException e) {
						if (!ringThreadToStop) {
							logger.error(e.getMessage(), e);
						}
					}

					try {
						// second data
						List<RetryDto> ringItemData = new ArrayList<>();
						int nowSecond = Calendar.getInstance().get(Calendar.SECOND);   // 避免处理耗时太长，跨过刻度，向前校验一个刻度；
						for (int i = 0; i < 2; i++) {
							List<RetryDto> tmpData = ringData.remove( (nowSecond+60-i)%60 );
							if (tmpData != null) {
								ringItemData.addAll(tmpData);
							}
						}

						// ring trigger
						logger.debug(">>>>>>>>>>> xxl-job, time-ring beat : " + nowSecond + " = " + Arrays.asList(ringItemData) );
						if (ringItemData.size() > 0) {
							// do trigger
							for (RetryDto retryDto: ringItemData) {
								// do trigger
								JobTriggerPoolHelper.trigger(retryDto.getJobId()
										, TriggerTypeEnum.RETRY
										, (retryDto.getExecutorFailRetryCount()-1)
										, retryDto.getExecutorShardingParam()
										, retryDto.getExecutorParam()
										, null);
							}
							// clear
							ringItemData.clear();
						}
					} catch (Exception e) {
						if (!ringThreadToStop) {
							logger.error(">>>>>>>>>>> xxl-job, JobRetryHelper#ringThread error:{}", e);
						}
					}
				}
				logger.info(">>>>>>>>>>> xxl-job, JobRetryHelper#ringThread stop");
			}
		});
		ringThread.setDaemon(true);
		ringThread.setName("xxl-job, admin JobRetryHelper#ringThread");
		ringThread.start();
	}

	private void pushTimeRing(int ringSecond, XxlJobLog xxlJobLog){
		RetryDto retryDto = new RetryDto(xxlJobLog.getJobId()
				, xxlJobLog.getId()
				, xxlJobLog.getExecutorFailRetryCount()
				, xxlJobLog.getExecutorParam()
				, xxlJobLog.getExecutorShardingParam());
		// push async ring
		List<RetryDto> ringItemData = ringData.get(ringSecond);
		if (ringItemData == null) {
			ringItemData = new ArrayList<RetryDto>();
			ringData.put(ringSecond, ringItemData);
		}
		ringItemData.add(retryDto);

		logger.debug(">>>>>>>>>>> xxl-job, schedule push time-ring : " + ringSecond + " = " + Arrays.asList(ringItemData) );
	}

	public void toStop(){

		// 1、stop schedule
		retryThreadToStop = true;
		try {
			TimeUnit.SECONDS.sleep(1);  // wait
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
		if (scheduleThread.getState() != Thread.State.TERMINATED){
			// interrupt and wait
			scheduleThread.interrupt();
			try {
				scheduleThread.join();
			} catch (InterruptedException e) {
				logger.error(e.getMessage(), e);
			}
		}

		// if has ring data
		boolean hasRingData = false;
		if (!ringData.isEmpty()) {
			for (int second : ringData.keySet()) {
				List<RetryDto> tmpData = ringData.get(second);
				if (tmpData!=null && tmpData.size()>0) {
					hasRingData = true;
					break;
				}
			}
		}
		if (hasRingData) {
			try {
				TimeUnit.SECONDS.sleep(8);
			} catch (InterruptedException e) {
				logger.error(e.getMessage(), e);
			}
		}

		// stop ring (wait job-in-memory stop)
		ringThreadToStop = true;
		try {
			TimeUnit.SECONDS.sleep(1);
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
		if (ringThread.getState() != Thread.State.TERMINATED){
			// interrupt and wait
			ringThread.interrupt();
			try {
				ringThread.join();
			} catch (InterruptedException e) {
				logger.error(e.getMessage(), e);
			}
		}

		logger.info(">>>>>>>>>>> xxl-job, JobRetryHelper stop");
	}

}
