package com.ctrip.ops.sysdev.inputs;

import com.ctrip.ops.sysdev.decoder.IDecode;
import com.ctrip.ops.sysdev.decoder.JsonDecoder;
import com.ctrip.ops.sysdev.decoder.PlainDecoder;
import com.ctrip.ops.sysdev.filters.BaseFilter;
import com.ctrip.ops.sysdev.outputs.BaseOutput;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xukunfeng on 16/3/26.
 */
public class Redis extends  BaseInput {

    private static final Logger logger = Logger
            .getLogger(Redis.class.getName());

    public  final int DEFAULT_THREAD_COUNT =  4;
    public  final int DEFAULT_REDIS_PORT = 6379;

    private int threadCount = DEFAULT_THREAD_COUNT;
    private String key;
    private String encoding;
    private String codec ;
    private IDecode decoder;
    private String redisHost ;
    private int redisPort;
    private ExecutorService executor;
    private AtomicInteger count = new AtomicInteger(0);
    private int sampleCount = -1;

    private JedisPool jedisPool ;

    public Redis(Map config, ArrayList<Map> filters, ArrayList<Map> outputs) throws Exception {
        super(config, filters, outputs);
        this.prepare();
    }


    public IDecode createDecoder() {
        if (this.codec.equals("json")){
            return new JsonDecoder();
        } else {
            return new PlainDecoder();
        }
    }



    private class Consumer implements Runnable {
        private IDecode decoder;

        private JedisPool jedisPool ;
        private String key ;
        private BaseFilter[] filterProcessors;
        private BaseOutput[] outputProcessors;


        public Consumer(Redis redis) {
            this.jedisPool = redis.jedisPool;
            this.key = redis.key;
            this.decoder = redis.createDecoder();
            this.filterProcessors = redis.createFilterProcessors();
            this.outputProcessors = redis.createOutputProcessors();
        }

        public void run() {
            try {
                logger.info("start thread to accept redis list");

                try(Jedis jedis = jedisPool.getResource()) {
                    while (true) {
                        List<String> message = null;
                        String m;

                        if ( sampleCount > 0   && count.incrementAndGet() > sampleCount  ){
                            break;
                        }

                        message = jedis.blpop(0, key);
                        m = message.get(1);

                        logger.debug("Redis Input: get message:" + count.addAndGet(1));

                        try {

                            long p1 = System.nanoTime();
                            Map<String, Object> event = this.decoder
                                    .decode(m);
                            long p2 = System.nanoTime();
                            String timeString;
                            timeString =  "decode=" +( p2-p1)  + ",";

                            if (this.filterProcessors != null) {
                                for (BaseFilter bf : filterProcessors) {
                                    if (event == null) {
                                        break;
                                    }
                                    p1 = System.nanoTime();
                                    event = bf.process(event);
                                    p2 = System.nanoTime();
                                    timeString = timeString + bf.getClass().getName() + "=" + (p2-p1) + ",";
                                }
                            }
                            if (event != null) {
                                for (BaseOutput bo : outputProcessors) {
                                    p1 = System.nanoTime();
                                    bo.process(event);
                                    p2 = System.nanoTime();
                                    timeString = timeString + bo.getClass().getName() + "=" + (p2-p1) + ",";
                                }
                            }
                        } catch (Exception e) {
                            logger.error("process event failed:" + m);
                            e.printStackTrace();
                            logger.error(e);
                        }
                    }
                }
            } catch (Throwable t) {
                logger.error(t);
                System.exit(1);
            }

        }
    }


    @Override
    protected void prepare() {


        String host = (String) this.config.get("host");

        String[] hp = host.split(":");
        String ip = null;
        int port = DEFAULT_REDIS_PORT;
        if (hp.length == 2) {
            host = hp[0];
            port = Integer.parseInt(hp[1]);
        } else if (hp.length == 1) {
            host = hp[0];
        }
        this.redisHost = host;
        this.redisPort = port;

        if( this.config.get("thread") != null){
            this.threadCount =  (Integer)this.config.get("thread");
        }
        //解析redis key
        this.key = (String)config.get("key");

        //解析数据格式
        String c = (String) this.config.get("codec");
        if (c != null && c.equalsIgnoreCase("plain")) {
            this.codec = c;
        }
        else {
            this.codec = "json";
        }


        logger.info("Redis Input:  redis host  = " + redisHost);
        logger.info("Redis Input:  redis port  = " + redisPort);
        logger.info("Redis Input:  codec       = " + codec);
        logger.info("Redis Input:  use thread count   = " + threadCount);
    }

    @Override
    public void emit(int sampleCount) {
        this.jedisPool = new JedisPool(this.redisHost,this.redisPort);

        this.sampleCount = sampleCount;
        if( sampleCount > 0 ){
            threadCount  = 1;
        }
        executor = Executors.newFixedThreadPool(threadCount);
        for(int i = 0; i < threadCount; i++){
            executor.execute(new Consumer(this));
        }
    }
}
