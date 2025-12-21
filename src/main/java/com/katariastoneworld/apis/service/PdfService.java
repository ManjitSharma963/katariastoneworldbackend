package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BillItemDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.entity.Seller;
import com.katariastoneworld.apis.entity.StateGstMaster;
import com.katariastoneworld.apis.repository.SellerRepository;
import com.katariastoneworld.apis.repository.StateGstMasterRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

@Service
public class PdfService {
    
    @Autowired
    private SellerRepository sellerRepository;
    
    @Autowired
    private StateGstMasterRepository stateGstMasterRepository;
    
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat DECIMAL_FORMAT_NO_DECIMAL = new DecimalFormat("#,##0");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    public byte[] generateBillPdf(BillResponseDTO bill) throws IOException {
        // Load HTML template
        String htmlTemplate = loadHtmlTemplate();
        
        // Get seller details from database
        Seller seller = sellerRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new RuntimeException("No seller information found in database. Please add seller details."));
        
        // Log seller details for verification
        System.out.println("Seller details fetched:");
        System.out.println("  - Name: " + seller.getSellerName());
        System.out.println("  - GSTIN: " + seller.getGstin());
        System.out.println("  - Mobile: " + seller.getMobile());
        System.out.println("  - Bank Name: " + seller.getBankName());
        System.out.println("  - Account Number: " + seller.getAccountNumber());
        System.out.println("  - IFSC Code: " + seller.getIfscCode());
        System.out.println("  - Address: " + (seller.getAddress() != null ? seller.getAddress().substring(0, Math.min(50, seller.getAddress().length())) + "..." : "null"));
        
        // Populate template with bill data
        String htmlContent = populateTemplate(htmlTemplate, bill, seller);
        
        // Convert HTML to PDF
        return convertHtmlToPdf(htmlContent);
    }
    
    public byte[] generateSimpleBillPdf(BillResponseDTO bill) throws IOException {
        // Load simple HTML template (no GST, no seller details)
        String htmlTemplate = loadSimpleHtmlTemplate();
        
        // Populate simple template with bill data (only items and total)
        String htmlContent = populateSimpleTemplate(htmlTemplate, bill);
        
        // Convert HTML to PDF
        return convertHtmlToPdf(htmlContent);
    }
    
    private String loadHtmlTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/bill-template.html");
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(StreamUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8);
        }
    }
    
    private String loadSimpleHtmlTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/simple-bill-template.html");
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(StreamUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8);
        }
    }
    
    private String populateTemplate(String template, BillResponseDTO bill, Seller seller) {
        String html = template;
        
        // Seller/Company Information from database
        String sellerGstin = seller.getGstin() != null && !seller.getGstin().trim().isEmpty() ? seller.getGstin() : "-";
        String sellerMobile = seller.getMobile() != null && !seller.getMobile().trim().isEmpty() ? seller.getMobile() : "-";
        String sellerName = seller.getSellerName() != null && !seller.getSellerName().trim().isEmpty() ? seller.getSellerName() : "-";
        String sellerAddress = seller.getAddress() != null && !seller.getAddress().trim().isEmpty() ? seller.getAddress().replace("\n", "<br />") : "-";
        
        // Bank details
        String bankName = seller.getBankName() != null ? seller.getBankName() : "";
        String accountNumber = seller.getAccountNumber() != null ? seller.getAccountNumber() : "";
        String ifscCode = seller.getIfscCode() != null ? seller.getIfscCode() : "";
        String subHeader = seller.getSubHeader() != null ? seller.getSubHeader() : "";
        String termsAndConditions = seller.getTermsAndConditions() != null ? seller.getTermsAndConditions().replace("\n", "<br />") : "";
        
        System.out.println("Populating seller information in template:");
        System.out.println("  - GSTIN: '" + sellerGstin + "'");
        System.out.println("  - Mobile: '" + sellerMobile + "'");
        System.out.println("  - Name: '" + sellerName + "'");
        System.out.println("  - Bank Name: '" + bankName + "'");
        System.out.println("  - Account Number: '" + accountNumber + "'");
        System.out.println("  - IFSC Code: '" + ifscCode + "'");
        System.out.println("  - Terms and Conditions: '" + (termsAndConditions != null && !termsAndConditions.isEmpty() ? termsAndConditions.substring(0, Math.min(100, termsAndConditions.length())) + "..." : "EMPTY OR NULL") + "'");
        
        html = html.replace("{{sellerGstin}}", sellerGstin);
        html = html.replace("{{sellerName}}", sellerName);
        html = html.replace("{{sellerAddress}}", sellerAddress);
        html = html.replace("{{sellerMobile}}", sellerMobile);
        html = html.replace("{{subHeader}}", subHeader);
        html = html.replace("{{bankName}}", bankName);
        html = html.replace("{{accountNumber}}", accountNumber);
        html = html.replace("{{ifscCode}}", ifscCode);
        html = html.replace("{{termsAndConditions}}", termsAndConditions);
        
        // Invoice Details
        html = html.replace("{{billNumber}}", bill.getBillNumber() != null ? bill.getBillNumber() : "");
        html = html.replace("{{billDate}}", bill.getBillDate() != null ? bill.getBillDate().format(DATE_FORMATTER) : "");
        
        // Buyer Order Info (default values - can be added to DTO later)
        html = html.replace("{{buyerOrderNo}}", "-");
        html = html.replace("{{bin}}", "-");
        html = html.replace("{{ewayBillNo}}", "-");
        
        // Extract states from addresses
        String stateOfOrigin = extractStateFromAddress(seller.getAddress());
        String stateOfDestination = extractStateFromAddress(bill.getAddress());
        
        System.out.println("Extracted states:");
        System.out.println("  - State of Origin (from seller address): '" + stateOfOrigin + "'");
        System.out.println("  - State of Destination (from customer address): '" + stateOfDestination + "'");
        
        // Fetch GST codes from state_gst_master table
        String sellerStateGstCode = "-";
        String buyerStateGstCode = "-";
        
        if (stateOfOrigin != null && !stateOfOrigin.isEmpty()) {
            StateGstMaster sellerStateGst = stateGstMasterRepository.findByStateNameIgnoreCase(stateOfOrigin).orElse(null);
            if (sellerStateGst != null) {
                sellerStateGstCode = sellerStateGst.getGstCode();
                System.out.println("  - Seller State GST Code: '" + sellerStateGstCode + "'");
            } else {
                System.out.println("  - Warning: Seller State GST Code not found for state: '" + stateOfOrigin + "'");
            }
        }
        
        if (stateOfDestination != null && !stateOfDestination.isEmpty()) {
            StateGstMaster buyerStateGst = stateGstMasterRepository.findByStateNameIgnoreCase(stateOfDestination).orElse(null);
            if (buyerStateGst != null) {
                buyerStateGstCode = buyerStateGst.getGstCode();
                System.out.println("  - Buyer State GST Code: '" + buyerStateGstCode + "'");
            } else {
                System.out.println("  - Warning: Buyer State GST Code not found for state: '" + stateOfDestination + "'");
            }
        }
        
        // State GST Codes - replace in template after fetching
        html = html.replace("{{sellerStateGstCode}}", sellerStateGstCode);
        html = html.replace("{{buyerStateGstCode}}", buyerStateGstCode);
        
        // Transport Details
        html = html.replace("{{preCarriageBy}}", "ROAD");
        html = html.replace("{{transportName}}", "By Truck");
        html = html.replace("{{stateOfOrigin}}", stateOfOrigin != null && !stateOfOrigin.isEmpty() ? stateOfOrigin : "-");
        html = html.replace("{{stateOfDestination}}", stateOfDestination != null && !stateOfDestination.isEmpty() ? stateOfDestination : "-");
        html = html.replace("{{truckNo}}", "-");
        html = html.replace("{{placeOfSupply}}", bill.getAddress() != null && bill.getAddress().contains("GURGAON") ? "GURGAON" : "-");
        html = html.replace("{{termsOfDelivery}}", "CREDIT");
        
        // Port of Discharge: FINAL DESTINATION [ORIGIN STATE] - [DESTINATION STATE]
        String portOfDischarge = "FINAL DESTINATION";
        if (stateOfOrigin != null && !stateOfOrigin.isEmpty() && stateOfDestination != null && !stateOfDestination.isEmpty()) {
            portOfDischarge += "<br />" + stateOfOrigin + " - " + stateOfDestination;
        } else if (stateOfOrigin != null && !stateOfOrigin.isEmpty()) {
            portOfDischarge += "<br />" + stateOfOrigin;
        } else if (stateOfDestination != null && !stateOfDestination.isEmpty()) {
            portOfDischarge += "<br />" + stateOfDestination;
        }
        html = html.replace("{{portOfDischarge}}", portOfDischarge);
        
        // Customer/Buyer Information
        html = html.replace("{{customerName}}", bill.getCustomerName() != null ? escapeHtml(bill.getCustomerName()) : "");
        html = html.replace("{{address}}", bill.getAddress() != null ? escapeHtml(bill.getAddress()) : "");
        html = html.replace("{{customerMobileNumber}}", bill.getCustomerMobileNumber() != null ? bill.getCustomerMobileNumber() : "");
        html = html.replace("{{gstin}}", bill.getGstin() != null ? bill.getGstin() : "");
        
        // Determine if states are same or different for GST calculation
        boolean isSameState = stateOfOrigin != null && stateOfDestination != null 
                            && stateOfOrigin.equalsIgnoreCase(stateOfDestination);
        
        System.out.println("GST Calculation:");
        System.out.println("  - State of Origin: '" + stateOfOrigin + "'");
        System.out.println("  - State of Destination: '" + stateOfDestination + "'");
        System.out.println("  - Same State: " + isSameState);
        
        // Calculate tax based on state: Same state = divide into two parts (CGST+SGST), Different states = IGST (one tax)
        double taxAmount = bill.getTaxAmount() != null ? bill.getTaxAmount() : 0.0;
        double cgstAmount = 0.0;
        double sgstAmount = 0.0;
        double igstAmount = 0.0;
        
        if (isSameState && taxAmount > 0) {
            // Same state (intra-state): Divide tax into two parts (9% + 9% = 18%)
            cgstAmount = taxAmount / 2.0;
            sgstAmount = taxAmount / 2.0;
            System.out.println("  - Same state (intra-state): Dividing tax into two parts");
            System.out.println("  - CGST Amount (9%): " + cgstAmount);
            System.out.println("  - SGST Amount (9%): " + sgstAmount);
        } else if (taxAmount > 0) {
            // Different states (inter-state): Show as one tax IGST (18%)
            igstAmount = taxAmount;
            System.out.println("  - Different states (inter-state): Using IGST");
            System.out.println("  - IGST Amount (18%): " + igstAmount);
        }
        
        // Amounts
        html = html.replace("{{subtotal}}", bill.getSubtotal() != null ? DECIMAL_FORMAT_NO_DECIMAL.format(bill.getSubtotal()) : "0");
        html = html.replace("{{taxPercentage}}", bill.getTaxPercentage() != null ? DECIMAL_FORMAT_NO_DECIMAL.format(bill.getTaxPercentage()) : "0");
        html = html.replace("{{taxAmount}}", bill.getTaxAmount() != null ? DECIMAL_FORMAT_NO_DECIMAL.format(bill.getTaxAmount()) : "0");
        html = html.replace("{{cgstAmount}}", DECIMAL_FORMAT_NO_DECIMAL.format(cgstAmount));
        html = html.replace("{{sgstAmount}}", DECIMAL_FORMAT_NO_DECIMAL.format(sgstAmount));
        html = html.replace("{{igstAmount}}", DECIMAL_FORMAT_NO_DECIMAL.format(igstAmount));
        html = html.replace("{{totalAmount}}", bill.getTotalAmount() != null ? DECIMAL_FORMAT_NO_DECIMAL.format(bill.getTotalAmount()) : "0");
        
        // Amount in words
        String amountInWords = convertNumberToWords(bill.getTotalAmount() != null ? bill.getTotalAmount() : 0.0);
        html = html.replace("{{amountInWords}}", amountInWords);
        
        // Handle conditional sections - remove conditionals and keep content if condition is true
        // Process conditionals in reverse order to avoid index issues
        // Discount amount
        if (bill.getDiscountAmount() == null || bill.getDiscountAmount() == 0) {
            html = removeConditionalBlock(html, "{{#if discountAmount}}", "{{/if}}");
        } else {
            html = removeConditionalBlock(html, "{{#if discountAmount}}", "{{/if}}", true);
        }
        
        // Service charge
        if (bill.getServiceCharge() == null || bill.getServiceCharge() == 0) {
            html = removeConditionalBlock(html, "{{#if serviceCharge}}", "{{/if}}");
        } else {
            html = removeConditionalBlock(html, "{{#if serviceCharge}}", "{{/if}}", true);
        }
        
        // CGST and SGST (same state)
        if (cgstAmount > 0) {
            html = removeConditionalBlock(html, "{{#if cgstAmount}}", "{{/if}}", true);
        } else {
            html = removeConditionalBlock(html, "{{#if cgstAmount}}", "{{/if}}");
        }
        
        // IGST (different states)
        if (igstAmount > 0) {
            html = removeConditionalBlock(html, "{{#if igstAmount}}", "{{/if}}", true);
        } else {
            html = removeConditionalBlock(html, "{{#if igstAmount}}", "{{/if}}");
        }
        
        // Tax amount (legacy - keep for backward compatibility)
        if (bill.getTaxAmount() == null || bill.getTaxAmount() == 0) {
            html = removeConditionalBlock(html, "{{#if taxAmount}}", "{{/if}}");
        } else {
            html = removeConditionalBlock(html, "{{#if taxAmount}}", "{{/if}}", true);
        }
        
        // GSTIN
        if (bill.getGstin() == null || bill.getGstin().trim().isEmpty()) {
            html = removeConditionalBlock(html, "{{#if gstin}}", "{{/if}}");
        } else {
            html = removeConditionalBlock(html, "{{#if gstin}}", "{{/if}}", true);
        }
        
        // Address
        if (bill.getAddress() == null || bill.getAddress().trim().isEmpty()) {
            html = removeConditionalBlock(html, "{{#if address}}", "{{/if}}");
        } else {
            html = removeConditionalBlock(html, "{{#if address}}", "{{/if}}", true);
        }
        
        // Customer name
        if (bill.getCustomerName() == null || bill.getCustomerName().trim().isEmpty()) {
            html = removeConditionalBlock(html, "{{#if customerName}}", "{{/if}}");
        } else {
            html = removeConditionalBlock(html, "{{#if customerName}}", "{{/if}}", true);
        }
        
        // Replace items section
        StringBuilder itemsHtml = new StringBuilder();
        if (bill.getItems() != null && !bill.getItems().isEmpty()) {
            int srNo = 1;
            for (BillItemDTO item : bill.getItems()) {
                String itemName = item.getItemName() != null ? item.getItemName() : "-";
                String category = item.getCategory() != null ? item.getCategory() : "-";
                double quantity = item.getQuantity() != null ? item.getQuantity() : 0.0;
                double pricePerUnit = item.getPricePerUnit() != null ? item.getPricePerUnit() : 0.0;
                double itemTotal = pricePerUnit * quantity;
                
                // Use product ID as HSN code
                String hsnCode = item.getProductId() != null ? String.valueOf(item.getProductId()) : "-";
                
                itemsHtml.append("<tr>");
                itemsHtml.append("<td class=\"text-center\">").append(srNo).append("</td>");
                itemsHtml.append("<td class=\"text-center\">").append(hsnCode).append("</td>");
                itemsHtml.append("<td>").append(escapeHtml(itemName)).append("</td>");
                itemsHtml.append("<td class=\"text-right\">").append(DECIMAL_FORMAT.format(quantity)).append("</td>");
                itemsHtml.append("<td class=\"text-right\">").append(DECIMAL_FORMAT.format(pricePerUnit)).append("</td>");
                itemsHtml.append("<td class=\"text-right\">").append(DECIMAL_FORMAT.format(itemTotal)).append("</td>");
                itemsHtml.append("</tr>");
                srNo++;
            }
        }
        
        // Replace the items loop section
        if (html.contains("{{#each items}}")) {
            int startIdx = html.indexOf("{{#each items}}");
            int endIdx = html.indexOf("{{/each}}", startIdx);
            if (endIdx != -1) {
                endIdx += "{{/each}}".length();
                String before = html.substring(0, startIdx);
                String after = html.substring(endIdx);
                html = before + itemsHtml.toString() + after;
            }
        }
        
        // Final cleanup - remove any remaining template markers (but not already replaced values)
        html = html.replaceAll("\\{\\{#if [^}]+\\}\\}", "");
        html = html.replaceAll("\\{\\{/if\\}\\}", "");
        html = html.replaceAll("\\{\\{#each [^}]+\\}\\}", "");
        html = html.replaceAll("\\{\\{/each\\}\\}", "");
        
        // Check for any remaining unreplaced placeholders and log them
        java.util.regex.Pattern placeholderPattern = java.util.regex.Pattern.compile("\\{\\{[^}]+\\}\\}");
        java.util.regex.Matcher matcher = placeholderPattern.matcher(html);
        boolean foundUnreplaced = false;
        while (matcher.find()) {
            if (!foundUnreplaced) {
                System.err.println("WARNING: Found unreplaced placeholders in template:");
                foundUnreplaced = true;
            }
            System.err.println("  - " + matcher.group());
        }
        
        // Remove any remaining placeholders (shouldn't happen if all are replaced)
        html = html.replaceAll("\\{\\{[^}]+\\}\\}", "");
        
        // Ensure all self-closing tags are properly formatted
        html = html.replaceAll("<br>", "<br />");
        html = html.replaceAll("<br\\s+/>", "<br />");
        
        // Escape any remaining unescaped & characters (but not &amp;, &lt;, &gt;, etc.)
        // This regex finds & that are not part of an entity
        html = html.replaceAll("&(?!(?:amp|lt|gt|quot|#\\d+|#x[0-9a-fA-F]+);)", "&amp;");
        
        System.out.println("Template populated successfully. Final HTML length: " + html.length());
        return html;
    }
    
    private String populateSimpleTemplate(String template, BillResponseDTO bill) {
        String html = template;
        
        // Bill basic info
        html = html.replace("{{billNumber}}", bill.getBillNumber() != null ? bill.getBillNumber() : "");
        html = html.replace("{{billDate}}", bill.getBillDate() != null ? bill.getBillDate().format(DATE_FORMATTER) : "");
        
        // Replace items section
        StringBuilder itemsHtml = new StringBuilder();
        if (bill.getItems() != null && !bill.getItems().isEmpty()) {
            int srNo = 1;
            for (BillItemDTO item : bill.getItems()) {
                String itemName = item.getItemName() != null ? item.getItemName() : "-";
                double quantity = item.getQuantity() != null ? item.getQuantity() : 0.0;
                double pricePerUnit = item.getPricePerUnit() != null ? item.getPricePerUnit() : 0.0;
                double itemTotal = pricePerUnit * quantity;
                
                itemsHtml.append("<tr>");
                itemsHtml.append("<td class=\"text-center\">").append(srNo).append("</td>");
                itemsHtml.append("<td>").append(escapeHtml(itemName)).append("</td>");
                itemsHtml.append("<td class=\"text-right\">").append(DECIMAL_FORMAT.format(quantity)).append("</td>");
                itemsHtml.append("<td class=\"text-right\">").append(DECIMAL_FORMAT.format(pricePerUnit)).append("</td>");
                itemsHtml.append("<td class=\"text-right\">").append(DECIMAL_FORMAT.format(itemTotal)).append("</td>");
                itemsHtml.append("</tr>");
                srNo++;
            }
        }
        
        // Replace the items loop section
        if (html.contains("{{#each items}}")) {
            int startIdx = html.indexOf("{{#each items}}");
            int endIdx = html.indexOf("{{/each}}", startIdx);
            if (endIdx != -1) {
                endIdx += "{{/each}}".length();
                String before = html.substring(0, startIdx);
                String after = html.substring(endIdx);
                html = before + itemsHtml.toString() + after;
            }
        }
        
        // Total amount
        html = html.replace("{{totalAmount}}", bill.getTotalAmount() != null ? DECIMAL_FORMAT.format(bill.getTotalAmount()) : "0.00");
        
        // Final cleanup - remove any remaining template markers
        html = html.replaceAll("\\{\\{#if [^}]+\\}\\}", "");
        html = html.replaceAll("\\{\\{/if\\}\\}", "");
        html = html.replaceAll("\\{\\{#each [^}]+\\}\\}", "");
        html = html.replaceAll("\\{\\{/each\\}\\}", "");
        html = html.replaceAll("\\{\\{[^}]+\\}\\}", ""); // Remove any remaining placeholders
        
        // Ensure all self-closing tags are properly formatted
        html = html.replaceAll("<br>", "<br />");
        html = html.replaceAll("<br\\s+/>", "<br />");
        
        // Escape any remaining unescaped & characters
        html = html.replaceAll("&(?!(?:amp|lt|gt|quot|#\\d+|#x[0-9a-fA-F]+);)", "&amp;");
        
        System.out.println("Simple template populated successfully. Final HTML length: " + html.length());
        return html;
    }
    
    private String removeConditionalBlock(String html, String startMarker, String endMarker) {
        return removeConditionalBlock(html, startMarker, endMarker, false);
    }
    
    private String removeConditionalBlock(String html, String startMarker, String endMarker, boolean keepContent) {
        int startIdx = html.indexOf(startMarker);
        if (startIdx == -1) return html;
        
        int endIdx = html.indexOf(endMarker, startIdx);
        if (endIdx == -1) return html;
        
        if (keepContent) {
            // Remove only the markers, keep the content
            String before = html.substring(0, startIdx);
            String content = html.substring(startIdx + startMarker.length(), endIdx);
            String after = html.substring(endIdx + endMarker.length());
            return before + content + after;
        } else {
            // Remove the entire block including markers and content
            endIdx += endMarker.length();
            return html.substring(0, startIdx) + html.substring(endIdx);
        }
    }
    
    private String convertNumberToWords(double amount) {
        long rupees = (long) amount;
        long paise = Math.round((amount - rupees) * 100);
        
        String rupeesInWords = numberToWords(rupees);
        String result = rupeesInWords + " RUPEES";
        
        if (paise > 0) {
            String paiseInWords = numberToWords(paise);
            result += " AND " + paiseInWords + " PAISE";
        }
        
        result += " ONLY";
        return result.toUpperCase();
    }
    
    private String numberToWords(long number) {
        if (number == 0) return "ZERO";
        
        String[] ones = {"", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN",
                         "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN", "EIGHTEEN", "NINETEEN"};
        String[] tens = {"", "", "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY", "NINETY"};
        
        if (number < 20) {
            return ones[(int) number];
        } else if (number < 100) {
            return tens[(int) (number / 10)] + (number % 10 != 0 ? " " + ones[(int) (number % 10)] : "");
        } else if (number < 1000) {
            return ones[(int) (number / 100)] + " HUNDRED" + (number % 100 != 0 ? " " + numberToWords(number % 100) : "");
        } else if (number < 100000) {
            return numberToWords(number / 1000) + " THOUSAND" + (number % 1000 != 0 ? " " + numberToWords(number % 1000) : "");
        } else if (number < 10000000) {
            return numberToWords(number / 100000) + " LAKH" + (number % 100000 != 0 ? " " + numberToWords(number % 100000) : "");
        } else {
            return numberToWords(number / 10000000) + " CRORE" + (number % 10000000 != 0 ? " " + numberToWords(number % 10000000) : "");
        }
    }
    
    private String extractStateFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }
        
        // List of Indian states (common variations)
        String[] states = {
            "ANDHRA PRADESH", "ARUNACHAL PRADESH", "ASSAM", "BIHAR", "CHHATTISGARH",
            "GOA", "GUJARAT", "HARYANA", "HIMACHAL PRADESH", "JHARKHAND",
            "KARNATAKA", "KERALA", "MADHYA PRADESH", "MAHARASHTRA", "MANIPUR",
            "MEGHALAYA", "MIZORAM", "NAGALAND", "ODISHA", "PUNJAB",
            "RAJASTHAN", "SIKKIM", "TAMIL NADU", "TELANGANA", "TRIPURA",
            "UTTAR PRADESH", "UTTARAKHAND", "WEST BENGAL",
            "DELHI", "JAMMU AND KASHMIR", "LADAKH", "PUDUCHERRY"
        };
        
        // Find pincode (6-digit number)
        java.util.regex.Pattern pincodePattern = java.util.regex.Pattern.compile("\\b(\\d{6})\\b");
        java.util.regex.Matcher matcher = pincodePattern.matcher(address);
        
        if (matcher.find()) {
            int pincodeIndex = matcher.start();
            // Get text before pincode
            String beforePincode = address.substring(0, pincodeIndex).trim();
            
            // Try to find state name in the text before pincode
            String upperAddress = beforePincode.toUpperCase();
            for (String state : states) {
                if (upperAddress.contains(state)) {
                    return state;
                }
            }
            
            // If state not found, try to extract the last word/city before pincode
            // Split by common separators (comma, newline, etc.)
            String[] parts = beforePincode.split("[,;\\n\\r]+");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1].trim();
                // Remove common suffixes like "Dist", "District", etc.
                lastPart = lastPart.replaceAll("\\s*(DIST|DISTRICT|CITY|STATE)\\s*$", "").trim();
                if (!lastPart.isEmpty()) {
                    return lastPart.toUpperCase();
                }
            }
            
            // Fallback: return last significant word before pincode
            String[] words = beforePincode.split("\\s+");
            if (words.length > 0) {
                String lastWord = words[words.length - 1].trim();
                if (lastWord.length() > 2) { // Ignore very short words
                    return lastWord.toUpperCase();
                }
            }
        }
        
        // If no pincode found, try to find state name in entire address
        String upperAddress = address.toUpperCase();
        for (String state : states) {
            if (upperAddress.contains(state)) {
                return state;
            }
        }
        
        return null;
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        // First escape & that are not already part of entities
        // This regex matches & that are not followed by a valid entity
        text = text.replaceAll("&(?!(?:amp|lt|gt|quot|#\\d+|#x[0-9a-fA-F]+);)", "&amp;");
        // Then escape other special characters
        text = text.replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
        return text;
    }
    
    private byte[] convertHtmlToPdf(String html) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            System.out.println("Converting HTML to PDF. HTML length: " + html.length());
            
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            
            byte[] pdfBytes = outputStream.toByteArray();
            System.out.println("PDF generated successfully. Size: " + pdfBytes.length + " bytes");
            return pdfBytes;
        } catch (Exception e) {
            System.err.println("Error converting HTML to PDF: " + e.getMessage());
            System.err.println("HTML content preview (first 500 chars): " + 
                (html != null && html.length() > 500 ? html.substring(0, 500) : html));
            e.printStackTrace();
            throw new IOException("Failed to convert HTML to PDF: " + e.getMessage(), e);
        }
    }
}

