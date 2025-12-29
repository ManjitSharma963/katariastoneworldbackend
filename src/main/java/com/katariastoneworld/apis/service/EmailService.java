package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BillResponseDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private PdfService pdfService;
    
    private static final String FROM_EMAIL = "manjitsharma963@gmail.com";
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    
    @Async
    public void sendBillEmail(BillResponseDTO bill, String customerEmail) {
        System.out.println("[ASYNC] Starting email sending process for bill: " + bill.getBillNumber());
        
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            System.out.println("[ASYNC] No email address provided for customer. Skipping email send.");
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(FROM_EMAIL);
            helper.setTo(customerEmail);
            helper.setSubject("Invoice #" + bill.getBillNumber() + " - Kataria Stone World");
            
            // Generate PDF first (before email body to catch errors early)
            byte[] pdfBytes = null;
            String pdfFileName = "Invoice_" + bill.getBillNumber() + ".pdf";
            boolean pdfGenerated = false;
            
            try {
                System.out.println("Generating PDF for bill: " + bill.getBillNumber());
                // Check if this is a simple bill (no GST, no seller details)
                // Use simple bill if: simpleBill flag is true OR taxPercentage is 0
                boolean isSimpleBill = (bill.getSimpleBill() != null && bill.getSimpleBill()) 
                                     || (bill.getTaxPercentage() != null && bill.getTaxPercentage() == 0);
                if (isSimpleBill) {
                    System.out.println("Generating simple bill PDF (no GST, no seller details)");
                    pdfBytes = pdfService.generateSimpleBillPdf(bill);
                } else {
                    pdfBytes = pdfService.generateBillPdf(bill);
                }
                if (pdfBytes != null && pdfBytes.length > 0) {
                    pdfGenerated = true;
                    System.out.println("PDF generated successfully. Size: " + pdfBytes.length + " bytes");
                } else {
                    System.err.println("PDF generation returned empty or null bytes");
                }
            } catch (Exception e) {
                System.err.println("Failed to generate PDF attachment: " + e.getMessage());
                e.printStackTrace();
                // Continue - will send email without PDF
            }
            
            // Generate email body
            String emailBody = generateBillEmailBody(bill, pdfGenerated);
            helper.setText(emailBody, true);
            
            // Attach PDF if generated successfully
            if (pdfGenerated && pdfBytes != null) {
                helper.addAttachment(pdfFileName, new ByteArrayResource(pdfBytes), "application/pdf");
                System.out.println("PDF attachment added to email: " + pdfFileName);
            } else {
                System.out.println("Email will be sent without PDF attachment due to generation failure");
            }
            
            mailSender.send(message);
            if (pdfGenerated) {
                System.out.println("[ASYNC] Bill email with PDF attachment sent successfully to: " + customerEmail);
            } else {
                System.out.println("[ASYNC] Bill email sent successfully to: " + customerEmail + " (without PDF attachment)");
            }
        } catch (MessagingException e) {
            System.err.println("Error sending bill email to " + customerEmail + ": " + e.getMessage());
            e.printStackTrace();
            // Don't throw exception - email failure shouldn't break bill creation
        } catch (Exception e) {
            System.err.println("Unexpected error sending bill email: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String generateBillEmailBody(BillResponseDTO bill, boolean hasPdfAttachment) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".container { max-width: 800px; margin: 0 auto; padding: 20px; }");
        html.append(".header { background-color: #2c3e50; color: white; padding: 20px; text-align: center; }");
        html.append(".content { background-color: #f9f9f9; padding: 20px; margin-top: 20px; }");
        html.append(".bill-info { background-color: white; padding: 15px; margin: 10px 0; border-left: 4px solid #2c3e50; }");
        html.append(".customer-info { background-color: white; padding: 15px; margin: 10px 0; }");
        html.append(".items-table { width: 100%; border-collapse: collapse; margin: 20px 0; background-color: white; }");
        html.append(".items-table th { background-color: #2c3e50; color: white; padding: 12px; text-align: left; }");
        html.append(".items-table td { padding: 10px; border-bottom: 1px solid #ddd; }");
        html.append(".items-table tr:hover { background-color: #f5f5f5; }");
        html.append(".summary { background-color: white; padding: 15px; margin: 10px 0; }");
        html.append(".summary-row { display: flex; justify-content: space-between; padding: 8px 0; }");
        html.append(".total-row { font-weight: bold; font-size: 1.2em; border-top: 2px solid #2c3e50; padding-top: 10px; }");
        html.append(".footer { text-align: center; margin-top: 30px; color: #666; font-size: 0.9em; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='container'>");
        
        // Header
        html.append("<div class='header'>");
        html.append("<h1>Kataria Stone World</h1>");
        html.append("<h2>Invoice</h2>");
        html.append("</div>");
        
        // Bill Info
        html.append("<div class='content'>");
        html.append("<div class='bill-info'>");
        html.append("<strong>Invoice Number:</strong> ").append(bill.getBillNumber()).append("<br>");
        html.append("<strong>Invoice Date:</strong> ").append(bill.getBillDate().format(DATE_FORMATTER)).append("<br>");
        html.append("<strong>Invoice Type:</strong> ").append(bill.getBillType()).append("<br>");
        html.append("<strong>Payment Status:</strong> ").append(bill.getPaymentStatus()).append("<br>");
        html.append("</div>");
        
        // Customer Info
        html.append("<div class='customer-info'>");
        html.append("<h3>Bill To:</h3>");
        if (bill.getCustomerName() != null && !bill.getCustomerName().trim().isEmpty()) {
            html.append("<strong>").append(bill.getCustomerName()).append("</strong><br>");
        }
        if (bill.getAddress() != null && !bill.getAddress().trim().isEmpty()) {
            html.append(bill.getAddress()).append("<br>");
        }
        html.append("<strong>Phone:</strong> ").append(bill.getCustomerMobileNumber()).append("<br>");
        if (bill.getGstin() != null && !bill.getGstin().trim().isEmpty()) {
            html.append("<strong>GSTIN:</strong> ").append(bill.getGstin()).append("<br>");
        }
        html.append("</div>");
        
        // Items Table
        html.append("<table class='items-table'>");
        html.append("<thead>");
        html.append("<tr>");
        html.append("<th>Item</th>");
        html.append("<th>Category</th>");
        html.append("<th>Quantity (Sqft)</th>");
        html.append("<th>Price/Sqft</th>");
        html.append("<th>Total</th>");
        html.append("</tr>");
        html.append("</thead>");
        html.append("<tbody>");
        
        if (bill.getItems() != null) {
            for (var item : bill.getItems()) {
                html.append("<tr>");
                html.append("<td>").append(item.getItemName() != null ? item.getItemName() : "-").append("</td>");
                html.append("<td>").append(item.getCategory() != null ? item.getCategory() : "-").append("</td>");
                html.append("<td>").append(item.getQuantity() != null ? item.getQuantity() : 0).append("</td>");
                html.append("<td>₹").append(item.getPricePerUnit() != null ? DECIMAL_FORMAT.format(item.getPricePerUnit()) : "0.00").append("</td>");
                double itemTotal = (item.getPricePerUnit() != null ? item.getPricePerUnit() : 0) * 
                                  (item.getQuantity() != null ? item.getQuantity() : 0);
                html.append("<td>₹").append(DECIMAL_FORMAT.format(itemTotal)).append("</td>");
                html.append("</tr>");
            }
        }
        
        html.append("</tbody>");
        html.append("</table>");
        
        // Summary
        html.append("<div class='summary'>");
        html.append("<h3>Summary</h3>");
        html.append("<div class='summary-row'>");
        html.append("<span>Total Sqft:</span>");
        html.append("<span>").append(bill.getTotalSqft() != null ? DECIMAL_FORMAT.format(bill.getTotalSqft()) : "0.00").append("</span>");
        html.append("</div>");
        html.append("<div class='summary-row'>");
        html.append("<span>Subtotal:</span>");
        html.append("<span>₹").append(bill.getSubtotal() != null ? DECIMAL_FORMAT.format(bill.getSubtotal()) : "0.00").append("</span>");
        html.append("</div>");
        
        if (bill.getTaxPercentage() != null && bill.getTaxPercentage() > 0) {
            html.append("<div class='summary-row'>");
            html.append("<span>Tax (").append(bill.getTaxPercentage()).append("%):</span>");
            html.append("<span>₹").append(bill.getTaxAmount() != null ? DECIMAL_FORMAT.format(bill.getTaxAmount()) : "0.00").append("</span>");
            html.append("</div>");
        }
        
        if (bill.getServiceCharge() != null && bill.getServiceCharge() > 0) {
            html.append("<div class='summary-row'>");
            html.append("<span>Service Charge:</span>");
            html.append("<span>₹").append(DECIMAL_FORMAT.format(bill.getServiceCharge())).append("</span>");
            html.append("</div>");
        }
        
        if (bill.getLabourCharge() != null && bill.getLabourCharge() > 0) {
            html.append("<div class='summary-row'>");
            html.append("<span>Labour Charge:</span>");
            html.append("<span>₹").append(DECIMAL_FORMAT.format(bill.getLabourCharge())).append("</span>");
            html.append("</div>");
        }
        
        if (bill.getTransportationCharge() != null && bill.getTransportationCharge() > 0) {
            html.append("<div class='summary-row'>");
            html.append("<span>Transportation Charge:</span>");
            html.append("<span>₹").append(DECIMAL_FORMAT.format(bill.getTransportationCharge())).append("</span>");
            html.append("</div>");
        }
        
        if (bill.getDiscountAmount() != null && bill.getDiscountAmount() > 0) {
            html.append("<div class='summary-row'>");
            html.append("<span>Discount:</span>");
            html.append("<span>-₹").append(DECIMAL_FORMAT.format(bill.getDiscountAmount())).append("</span>");
            html.append("</div>");
        }
        
        html.append("<div class='summary-row total-row'>");
        html.append("<span>Total Amount:</span>");
        html.append("<span>₹").append(bill.getTotalAmount() != null ? DECIMAL_FORMAT.format(bill.getTotalAmount()) : "0.00").append("</span>");
        html.append("</div>");
        html.append("</div>");
        
        // Footer
        html.append("<div class='footer'>");
        if (hasPdfAttachment) {
            html.append("<p><strong>Please find the detailed invoice attached as PDF.</strong></p>");
        }
        html.append("<p>Thank you for your business!</p>");
        html.append("<p>Kataria Stone World</p>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }
    
    public void sendTestEmail(String toEmail) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            toEmail = FROM_EMAIL;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(FROM_EMAIL);
            helper.setTo(toEmail);
            helper.setSubject("Test Email - Kataria Stone World API");
            
            String emailBody = "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<meta charset='UTF-8'>" +
                    "<style>" +
                    "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                    ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                    ".header { background-color: #2c3e50; color: white; padding: 20px; text-align: center; }" +
                    ".content { background-color: #f9f9f9; padding: 20px; margin-top: 20px; }" +
                    ".success { background-color: #d4edda; color: #155724; padding: 15px; border-radius: 5px; margin: 20px 0; }" +
                    "</style>" +
                    "</head>" +
                    "<body>" +
                    "<div class='container'>" +
                    "<div class='header'>" +
                    "<h1>Kataria Stone World</h1>" +
                    "<h2>Email Test</h2>" +
                    "</div>" +
                    "<div class='content'>" +
                    "<div class='success'>" +
                    "<h3>✅ Email Configuration Successful!</h3>" +
                    "<p>This is a test email to verify that the email service is working correctly.</p>" +
                    "<p><strong>From:</strong> " + FROM_EMAIL + "</p>" +
                    "<p><strong>To:</strong> " + toEmail + "</p>" +
                    "<p><strong>Time:</strong> " + java.time.LocalDateTime.now() + "</p>" +
                    "</div>" +
                    "<p>If you received this email, it means:</p>" +
                    "<ul>" +
                    "<li>SMTP configuration is correct</li>" +
                    "<li>Email service is working properly</li>" +
                    "<li>Bills can be sent to customers via email</li>" +
                    "</ul>" +
                    "<p>Thank you for testing!</p>" +
                    "</div>" +
                    "</div>" +
                    "</body>" +
                    "</html>";
            
            helper.setText(emailBody, true);
            
            mailSender.send(message);
            System.out.println("Test email sent successfully to: " + toEmail);
        } catch (MessagingException e) {
            System.err.println("Error sending test email to " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send test email: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("Unexpected error sending test email: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send test email: " + e.getMessage(), e);
        }
    }
}

