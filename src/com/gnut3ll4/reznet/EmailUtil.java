package com.gnut3ll4.reznet;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;


public class EmailUtil {

	public static void sendEmail(String username, String password, boolean smtpAuth, boolean starttlsEnable, String host, int port,String[] toEmail, String subject, String body){

		Properties props = new Properties();
		props.put("mail.smtp.auth", ""+smtpAuth);
		props.put("mail.smtp.starttls.enable", ""+starttlsEnable);
		props.put("mail.smtp.host", host);	
		props.put("mail.smtp.port", ""+port);

		Session session = Session.getInstance(props,
		  new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		  });

		try {

			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress("quota_internet@gmail.com"));
			
					
			for(String address : toEmail) {
				message.addRecipients(Message.RecipientType.CC, InternetAddress.parse(address));				
			}

			message.setSubject(subject);
			message.setText(body);

			Transport.send(message);

			System.out.println("Email sent");

		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
    }
}
