package com.foggyframework.fsscript;


import com.foggyframework.fsscript.support.PropertyProxy;
import lombok.Data;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication()
public class FoggyFrameworkFsscriptTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoggyFrameworkFsscriptTestApplication.class, args);
    }


    @Bean
    public Object importBeanTest() {
        return new PtTest();
    }
    @Bean
    public Object importBeanTest3() {
        return new PtTest();
    }

    @Data
    public static class PtTest {
        public String test() {
            return "ok";
        }

        public String test2(String aa) {
            return aa;
        }

        public String getTest3() {
            return "tx3";
        }

        public int testArg(PtTestArg aa) {
            return aa.b;
        }

        public PropertyProxy getTest4() {
            return new PropertyProxy(new PxTest());
        }
    }
@Data
    public static class PtTestArg{
        String a;
        int b;

    }

    @Data
    public static class PxTest {
        String a = "1";

        public String abc(String abc) {
            return abc;
        }

    }

}
