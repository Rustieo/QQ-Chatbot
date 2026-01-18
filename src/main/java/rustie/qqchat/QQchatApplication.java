package rustie.qqchat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("rustie.qqchat.mapper")
public class QQchatApplication {

    public static void main(String[] args) {
        SpringApplication.run(QQchatApplication.class, args);
    }

}
