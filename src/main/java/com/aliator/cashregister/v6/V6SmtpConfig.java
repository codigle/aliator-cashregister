package com.aliator.cashregister.v6;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

@Configuration
public class V6SmtpConfig {
    @Bean
    JavaMailSender registrationMailSender(
            @Value("${registration.smtp.host:}") String host,
            @Value("${registration.smtp.port:587}") int port,
            @Value("${registration.smtp.user:}") String user,
            @Value("${registration.smtp.password-b64:}") String encoded) {
        if (host.isBlank() || user.isBlank() || encoded.isBlank() || port != 587)
            throw new IllegalStateException("Registration SMTP needs host, user, password and STARTTLS port 587");
        final byte[] raw;
        try { raw = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid SMTP password encoding"); }
        String password = new String(raw, StandardCharsets.UTF_8);
        if (password.isBlank()) throw new IllegalStateException("SMTP password is empty");
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(host); mail.setPort(port); mail.setUsername(user); mail.setPassword(password);
        mail.setDefaultEncoding("UTF-8");
        Properties props = mail.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "7000");
        props.put("mail.smtp.writetimeout", "7000");
        props.put("mail.debug", "false");
        return mail;
    }
}
