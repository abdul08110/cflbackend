package com.friendsfantasy.fantasybackend.common;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.brand.name:Community Fantasy League}")
    private String brandName;

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(brandName + " - OTP Verification");
        message.setText("Hello,\n\n"
                + "Use this OTP to continue with your " + brandName + " request.\n\n"
                + "OTP: " + otp + "\n"
                + "Valid for: 5 minutes\n\n"
                + "If you did not request this OTP, you can ignore this email.\n\n"
                + "Regards,\n"
                + brandName + " Support");
        mailSender.send(message);
    }
}
