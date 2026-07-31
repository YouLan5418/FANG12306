package org.opengoofy.index12306;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 12306 重构版单体应用启动入口。
 * <p>
 * 业务域按包划分：user / ticket / order / pay / customerservice。
 */
@SpringBootApplication
@EnableScheduling
public class Index12306Application {

    public static void main(String[] args) {
        SpringApplication.run(Index12306Application.class, args);
    }
}
