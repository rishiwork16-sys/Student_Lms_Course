package com.finallms.backend.service;

import com.finallms.backend.entity.Certificate;
import com.finallms.backend.entity.Course;
import com.finallms.backend.entity.User;
import com.finallms.backend.repository.CertificateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

import org.apache.poi.xslf.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.springframework.core.io.ClassPathResource;

@Service
public class CertificateService {

    private static final Logger logger = LoggerFactory.getLogger(CertificateService.class);

    @Autowired
    private CertificateRepository certificateRepository;

    /**
     * Generate or retrieve existing certificate for a user-course pair
     */
    @Transactional
    public Certificate generateOrGetCertificate(User user, Course course) {
        // Check if certificate already exists
        return certificateRepository.findByUserAndCourse(user, course)
                .orElseGet(() -> {
                    // Create new certificate
                    Certificate certificate = new Certificate();
                    certificate.setUser(user);
                    certificate.setCourse(course);
                    certificate.setCertificateId(generateUniqueCertificateId());
                    certificate.setIssuedDate(LocalDateTime.now());
                    certificate
                            .setVerificationUrl("https://skilledup.tech/certificate/" + certificate.getCertificateId());

                    return certificateRepository.save(certificate);
                });
    }

    /**
     * Generate unique 5-digit certificate ID
     */
    private String generateUniqueCertificateId() {
        Random random = new Random();
        String certificateId;

        do {
            // Generate 5-digit number (10000-99999)
            int number = 10000 + random.nextInt(90000);
            certificateId = String.valueOf(number);
        } while (certificateRepository.existsByCertificateId(certificateId));

        return certificateId;
    }

    /**
     * Generate dynamic PPT certificate by replacing placeholders in the template
     */
    public byte[] generatePptCertificate(User user, Course course) throws IOException {
        String templatePath = "static/Final_Certificate.pptx";
        ClassPathResource resource = new ClassPathResource(templatePath);

        Certificate certificate = generateOrGetCertificate(user, course);

        try (InputStream is = resource.getInputStream();
                XMLSlideShow ppt = new XMLSlideShow(is)) {

            // Extract data with comprehensive logging
            String studentName = user.getName() != null ? user.getName().trim() : "Student";
            String courseTitle = course.getTitle() != null ? course.getTitle().trim() : "COURSE";
            Integer duration = course.getDuration() != null ? course.getDuration() : 0;
            String durationText = duration + "-month";

            // Log warnings if any data is null
            if (user.getName() == null || user.getName().trim().isEmpty()) {
                logger.warn("WARNING: User name is null or empty! User ID: {}, Phone: {}, Email: {}",
                        user.getId(), user.getPhone(), user.getEmail());
                System.out.println("⚠️ WARNING: Student name is NULL or EMPTY for user ID: " + user.getId());
            }
            if (course.getTitle() == null || course.getTitle().trim().isEmpty()) {
                logger.warn("WARNING: Course title is null or empty! Course ID: {}", course.getId());
                System.out.println("⚠️ WARNING: Course title is NULL or EMPTY for course ID: " + course.getId());
            }
            if (course.getDuration() == null) {
                logger.warn("WARNING: Course duration is null! Course ID: {}", course.getId());
                System.out.println("⚠️ WARNING: Course duration is NULL for course ID: " + course.getId());
            }

            // Format date as "30 Jul 2025" for the body to prevent wrap/overlap
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
            String completionDate = certificate.getIssuedDate().format(formatter);

            // Format date as "31.07.2025" for the issue date field
            DateTimeFormatter issueDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            String issueDate = certificate.getIssuedDate().format(issueDateFormatter);

            String certId = certificate.getCertificateId();
            String certUrl = certificate.getVerificationUrl();

            // Comprehensive logging of all certificate data
            logger.info("=== CERTIFICATE GENERATION DATA ===");
            logger.info("Student Name: '{}'", studentName);
            logger.info("Course Title: '{}'", courseTitle);
            logger.info("Duration: {} months ({})", duration, durationText);
            logger.info("Completion Date: '{}'", completionDate);
            logger.info("Issue Date: '{}'", issueDate);
            logger.info("Certificate ID: '{}'", certId);
            logger.info("Certificate URL: '{}'", certUrl);
            logger.info("===================================");

            System.out.println("\n=== CERTIFICATE DATA TO BE INSERTED ===");
            System.out.println("📝 Student Name: [" + studentName + "]");
            System.out.println("📚 Course Title: [" + courseTitle + "]");
            System.out.println("⏱️  Duration: [" + durationText + "]");
            System.out.println("📅 Completion Date: [" + completionDate + "]");
            System.out.println("🆔 Certificate ID: [" + certId + "]");
            System.out.println("========================================\n");

            // Iterate through slides
            for (XSLFSlide slide : ppt.getSlides()) {
                processShapes(slide.getShapes(), studentName, courseTitle, durationText, completionDate, issueDate,
                        certId, certUrl);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ppt.write(out);
                return out.toByteArray();
            }
        }
    }

    private void processShapes(List<XSLFShape> shapes, String studentName, String courseTitle, String durationText,
            String completionDate, String issueDate, String certId, String certUrl) {

        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFGroupShape) {
                processShapes(((XSLFGroupShape) shape).getShapes(), studentName, courseTitle, durationText,
                        completionDate, issueDate, certId, certUrl);
            } else if (shape instanceof XSLFTable) {
                XSLFTable table = (XSLFTable) shape;
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        processTextParagraphs(cell.getTextParagraphs(), studentName, courseTitle, durationText,
                                completionDate, issueDate, certId, certUrl);
                    }
                }
            } else if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                processTextParagraphs(textShape.getTextParagraphs(), studentName, courseTitle, durationText,
                        completionDate, issueDate, certId, certUrl);
            }
        }
    }

    private void processTextParagraphs(List<XSLFTextParagraph> paragraphs, String studentName, String courseTitle,
            String durationText,
            String completionDate, String issueDate, String certId, String certUrl) {

        for (XSLFTextParagraph paragraph : paragraphs) {
            List<XSLFTextRun> runs = paragraph.getTextRuns();
            if (runs.isEmpty())
                continue;

            // FIRST: Check individual runs for "Administrator" and replace immediately
            // This handles cases where the text might be in a single run
            for (XSLFTextRun run : runs) {
                String runText = run.getRawText();
                if (runText != null) {
                    String lowerRunText = runText.toLowerCase().trim();
                    if (lowerRunText.equals("administrator") ||
                            lowerRunText.contains("administrator") ||
                            lowerRunText.equals("vivek singh") ||
                            lowerRunText.contains("vivek singh")) {

                        logger.info("FOUND Administrator in individual run: '{}'. Replacing with: '{}'", runText,
                                studentName);

                        // Capture style before replacement
                        String fontFamily = run.getFontFamily();
                        Double fontSize = run.getFontSize();
                        boolean bold = run.isBold();
                        boolean italic = run.isItalic();

                        // Replace the text
                        run.setText(studentName);

                        // Apply premium styling for name
                        String fontName = "Great Vibes";
                        java.awt.Font testFont = new java.awt.Font(fontName, java.awt.Font.PLAIN, 10);
                        if (!testFont.getFamily().equalsIgnoreCase(fontName)) {
                            fontName = "Brush Script MT";
                        }

                        run.setFontFamily(fontName);
                        run.setBold(false);
                        run.setItalic(false);
                        run.setFontSize(52.0);

                        // Gold color
                        java.awt.Color goldColor = new java.awt.Color(201, 161, 59);
                        run.setFontColor(goldColor);

                        // Center the paragraph
                        paragraph.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                        paragraph.setLeftMargin(0.0);
                        paragraph.setIndent(0.0);

                        // Clear all other runs in this paragraph
                        for (int i = runs.size() - 1; i > runs.indexOf(run); i--) {
                            paragraph.removeTextRun(runs.get(i));
                        }
                        for (int i = runs.indexOf(run) - 1; i >= 0; i--) {
                            paragraph.removeTextRun(runs.get(i));
                        }

                        logger.info("Successfully replaced Administrator with: {}", studentName);
                        continue; // Move to next paragraph
                    }
                }
            }

            // SECOND: Do the paragraph-level concatenation and replacement (existing logic)
            StringBuilder sb = new StringBuilder();
            for (XSLFTextRun run : runs) {
                String txt = run.getRawText();
                if (txt != null)
                    sb.append(txt);
            }
            String fullText = sb.toString();
            String originalText = fullText;

            // LOG EVERYTHING for the name field investigation
            if (fullText.toLowerCase().contains("administrator") || fullText.toLowerCase().contains("student")) {
                logger.info(
                        "CRITICAL DEBUG: Processing paragraph with potential overlap: '{}'. Student Name to insert: '{}'",
                        fullText, studentName);
            }

            // Log found text for debugging (show ALL text to help identify placeholders)
            if (fullText.trim().length() > 0) {
                System.out.println("🔍 Template Text Found: '" + fullText + "'");
            }

            // Flags to track what kind of replacement happened
            boolean isDateReplacement = false;
            boolean isNameReplacement = false;
            boolean isUrlReplacement = false;

            // Replace hardcoded template values with dynamic data
            // Student Name replacement - be very aggressive
            // If the paragraph contains these keywords, we assume it's the name field
            String lowerText = fullText.toLowerCase().trim();
            if (lowerText.contains("administrator") ||
                    lowerText.contains("vivek singh") ||
                    lowerText.contains("student name") ||
                    lowerText.contains("{{student_name}}") ||
                    lowerText.contains("{{name}}") ||
                    lowerText.contains("{{studentname}}") ||
                    lowerText.equals("student")) {

                logger.info("MATCH Name Placeholder in text: '{}'. Replacing with: '{}'", fullText, studentName);
                fullText = studentName;
                isNameReplacement = true;
            } else if (lowerText.contains("{{student}}")) {
                fullText = studentName;
                isNameReplacement = true;
            }
            // Course Name - be very aggressive with multiple patterns
            boolean isCourseReplacement = false;

            // Pattern 1: Exact match for known template text
            if (fullText.contains("CareerX: Data Science & GenAI")
                    || fullText.contains("CareerX: Data Science &amp; GenAI")
                    || fullText.contains("MySQL & GenAI MasterClass with BigQuery")
                    || fullText.contains("MySQL &amp; GenAI MasterClass with BigQuery")) {
                fullText = fullText.replace("CareerX: Data Science & GenAI", courseTitle)
                        .replace("CareerX: Data Science &amp; GenAI", courseTitle)
                        .replace("MySQL & GenAI MasterClass with BigQuery", courseTitle)
                        .replace("MySQL &amp; GenAI MasterClass with BigQuery", courseTitle);
                isCourseReplacement = true;
            }

            // Pattern 2: Placeholder patterns
            if (fullText.contains("{{COURSE_NAME}}") || fullText.contains("{{CourseName}}")
                    || fullText.contains("{{course_name}}") || fullText.contains("{{coursename}}")) {
                fullText = fullText.replace("{{COURSE_NAME}}", courseTitle)
                        .replace("{{CourseName}}", courseTitle)
                        .replace("{{course_name}}", courseTitle)
                        .replace("{{coursename}}", courseTitle);
                isCourseReplacement = true;
            }

            // Pattern 3: Generic course name patterns (case-insensitive check)
            if (lowerText.contains("careerx") || lowerText.contains("data science")
                    || lowerText.contains("course name") || lowerText.contains("course title")
                    || lowerText.contains("mysql") || lowerText.contains("genai")
                    || lowerText.contains("masterclass with bigquery")) {
                logger.info("MATCH Course Placeholder in text: '{}'. Replacing with: '{}'", fullText, courseTitle);
                fullText = courseTitle;
                isCourseReplacement = true;
            }

            // Pattern 4: If text contains "course" and is relatively short (likely a
            // placeholder)
            if (!isCourseReplacement && lowerText.contains("course") && fullText.length() < 100
                    && !lowerText.contains("completed") && !lowerText.contains("successfully")) {
                logger.info("MATCH Generic Course text: '{}'. Replacing with: '{}'", fullText, courseTitle);
                fullText = courseTitle;
                isCourseReplacement = true;
            }

            // Duration - be very aggressive with multiple patterns
            boolean isDurationReplacement = false;

            // Pattern 1: Exact match for known template text
            if (fullText.contains("9-month") || fullText.contains("9 month")
                    || fullText.contains("2.5-hour MasterClass") || fullText.contains("2.5-hour")) {
                fullText = fullText.replace("9-month", durationText)
                        .replace("9 month", durationText)
                        .replace("2.5-hour MasterClass", durationText)
                        .replace("2.5-hour", durationText);
                isDurationReplacement = true;
            }

            // Pattern 2: Any number followed by "-month", " month", or "-hour"
            if (fullText.matches(".*\\d+-month.*") || fullText.matches(".*\\d+ month.*")
                    || fullText.matches(".*\\d+\\.?\\d*-hour.*")) {
                fullText = fullText.replaceAll("\\d+-month", durationText)
                        .replaceAll("\\d+ month", durationText)
                        .replaceAll("\\d+\\.?\\d*-hour", durationText);
                isDurationReplacement = true;
            }

            // Pattern 3: Placeholder patterns
            if (fullText.contains("{{DURATION}}") || fullText.contains("{{duration}}")
                    || fullText.contains("{{Duration}}")) {
                fullText = fullText.replace("{{DURATION}}", durationText)
                        .replace("{{duration}}", durationText)
                        .replace("{{Duration}}", durationText);
                isDurationReplacement = true;
            }

            // Pattern 4: Generic duration patterns
            if (lowerText.contains("month") && lowerText.contains("duration")) {
                logger.info("MATCH Duration Placeholder in text: '{}'. Replacing with: '{}'", fullText, durationText);
                fullText = durationText;
                isDurationReplacement = true;
            }
            // Completion Date - replace "30 July 2025" and "04 January 2026" with actual
            // date
            if (fullText.contains("30 July 2025") || fullText.contains("04 January 2026")) {
                fullText = fullText.replace("30 July 2025", completionDate)
                        .replace("04 January 2026", completionDate);
                isDateReplacement = true;
            }
            if (fullText.contains("{{COMPLETION_DATE}}") || fullText.contains("{{Date}}")) {
                fullText = fullText.replace("{{COMPLETION_DATE}}", completionDate)
                        .replace("{{Date}}", completionDate);
                isDateReplacement = true;
            }
            // Match any date pattern like "DD Month YYYY"
            if (fullText.matches(".*\\d{1,2}\\s+[A-Z][a-z]+\\s+\\d{4}.*")) {
                logger.info("MATCH Date Pattern in text: '{}'. Replacing with: '{}'", fullText, completionDate);
                fullText = fullText.replaceAll("\\d{1,2}\\s+[A-Z][a-z]+\\s+\\d{4}", completionDate);
                isDateReplacement = true;
            }
            // Issue Date - replace "31.07.2025" with actual issue date
            if (fullText.contains("31.07.2025")) {
                fullText = fullText.replace("31.07.2025", issueDate);
                isDateReplacement = true;
            }
            if (fullText.contains("{{ISSUE_DATE}}")) {
                fullText = fullText.replace("{{ISSUE_DATE}}", issueDate);
                isDateReplacement = true;
            }
            // Certificate ID - replace "34543" with actual cert ID
            if (fullText.contains("34543")) {
                fullText = fullText.replace("34543", certId);
            }
            // Certificate URL - replace the hardcoded URL
            if (fullText.contains("https://skilledup.tech/certificate/34543")) {
                fullText = fullText.replace("https://skilledup.tech/certificate/34543", certUrl);
                isUrlReplacement = true;
            }
            if (fullText.contains("{{CERT_ID}}")) {
                fullText = fullText.replace("{{CERT_ID}}", certId);
            }
            if (fullText.contains("{{CERT_URL}}")) {
                fullText = fullText.replace("{{CERT_URL}}", certUrl);
                isUrlReplacement = true;
            }

            if (isNameReplacement || isDateReplacement || isCourseReplacement || isDurationReplacement
                    || isUrlReplacement
                    || !fullText.equals(originalText)) {
                System.out.println("✅ REPLACEMENT DETECTED:");
                System.out.println("   Original: '" + originalText + "'");
                System.out.println("   New Text: '" + fullText + "'");
                String type = isNameReplacement ? "NAME"
                        : isDateReplacement ? "DATE"
                                : isCourseReplacement ? "COURSE" : isDurationReplacement ? "DURATION" : "OTHER";
                System.out.println("   Type: " + type);

                logger.info("Replacing text: '{}' -> '{}' (Type: {})", originalText, fullText, type);

                XSLFTextRun targetRun = runs.get(0);

                // Capture original style properties
                String originalFontFamily = targetRun.getFontFamily();
                Double originalFontSize = targetRun.getFontSize();
                boolean originalBold = targetRun.isBold();
                boolean originalItalic = targetRun.isItalic();
                org.apache.poi.sl.usermodel.PaintStyle originalColor = targetRun.getFontColor();

                System.out.println("DEBUG: Target Run Style - Font: " + originalFontFamily +
                        ", Size: " + originalFontSize +
                        ", Bold: " + originalBold +
                        ", Color: " + originalColor);

                // Set all texts to first run
                targetRun.setText(fullText);

                // CRITICAL: Clear all other runs in this paragraph to prevent leftover text
                // (e.g., if "Administrator" was split across multiple runs)
                for (int i = runs.size() - 1; i > 0; i--) {
                    paragraph.removeTextRun(runs.get(i));
                }

                // Re-apply original style properties or set custom ones
                if (isNameReplacement) {
                    logger.info("Applying Premium Styling to name: {}", studentName);

                    // Try to use 'Great Vibes', fallback to 'Brush Script MT' if missing
                    String fontName = "Great Vibes";
                    java.awt.Font testFont = new java.awt.Font(fontName, java.awt.Font.PLAIN, 10);
                    if (!testFont.getFamily().equalsIgnoreCase(fontName)) {
                        logger.warn("Font '{}' not found on system. Falling back to 'Brush Script MT'.", fontName);
                        fontName = "Brush Script MT";
                    }

                    targetRun.setFontFamily(fontName);
                    targetRun.setBold(false);
                    targetRun.setItalic(false);
                    targetRun.setFontSize(52.0);

                    // Setting Gold Color (#C9A13B)
                    java.awt.Color goldColor = new java.awt.Color(201, 161, 59);
                    targetRun.setFontColor(goldColor);

                    // --- Perfect Centering ---
                    paragraph.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                    paragraph.setLeftMargin(0.0);
                    paragraph.setIndent(0.0);
                    paragraph.setSpaceBefore(0.0);
                    paragraph.setSpaceAfter(0.0);
                } else {
                    if (originalFontFamily != null)
                        targetRun.setFontFamily(originalFontFamily);
                    if (originalFontSize != null)
                        targetRun.setFontSize(originalFontSize);

                    targetRun.setBold(originalBold);
                    targetRun.setItalic(originalItalic);

                    if (isDateReplacement || isUrlReplacement) {
                        targetRun.setFontColor(java.awt.Color.BLACK);
                        // Reduce font size slightly for date and URL to prevent overlap
                        if (originalFontSize != null) {
                            targetRun.setFontSize(originalFontSize - 2.0);
                        }
                    } else if (originalColor != null) {
                        targetRun.setFontColor(originalColor);
                    }
                }

                for (int i = 1; i < runs.size(); i++) {
                    runs.get(i).setText("");
                }
            }
        }
    }

    public byte[] generatePdfCertificate(User user, Course course) throws IOException {
        System.out.println("DEBUG: Entering generatePdfCertificate for user " + user.getPhone());
        logger.info("Starting PDF generation for user: {} course: {}", user.getPhone(), course.getId());
        byte[] pptBytes;
        try {
            pptBytes = generatePptCertificate(user, course);
        } catch (Exception e) {
            System.err.println("DEBUG: generatePptCertificate failed");
            e.printStackTrace();
            throw new IOException("Failed to generate PPT certificate", e);
        }
        logger.info("PPT generated, size: {}", pptBytes.length);

        try (InputStream is = new java.io.ByteArrayInputStream(pptBytes);
                XMLSlideShow ppt = new XMLSlideShow(is);
                PDDocument pdf = new PDDocument()) {

            Dimension pgsize = ppt.getPageSize();
            float width = (float) pgsize.getWidth();
            float height = (float) pgsize.getHeight();
            System.out.println("DEBUG: Slide size " + width + "x" + height);

            if (ppt.getSlides().isEmpty()) {
                System.err.println("DEBUG: No slides found in PPT!");
                throw new IOException("No slides found in template");
            }

            for (XSLFSlide slide : ppt.getSlides()) {
                logger.info("Rendering slide to image...");
                System.out.println("DEBUG: Rendering slide...");

                BufferedImage img = null;
                Graphics2D graphics = null;
                try {
                    // Reduced scale from 2.0 to 1.5 to avoid OutOfMemoryError
                    int scaleFactor = 1;
                    img = new BufferedImage(pgsize.width * scaleFactor, pgsize.height * scaleFactor,
                            BufferedImage.TYPE_INT_RGB);
                    graphics = img.createGraphics();

                    graphics.setPaint(java.awt.Color.white);
                    graphics.fill(new java.awt.Rectangle(0, 0, img.getWidth(), img.getHeight()));

                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                            RenderingHints.VALUE_FRACTIONALMETRICS_ON);

                    graphics.scale((double) scaleFactor, (double) scaleFactor);
                    slide.draw(graphics);
                    logger.debug("Slide drawn successfully");

                    // Create PDF page with correct orientation
                    // If certificate is landscape (width > height), ensure PDF page is also
                    // landscape
                    PDRectangle pageSize;
                    if (width > height) {
                        // Landscape orientation - use width as width, height as height
                        pageSize = new PDRectangle(width, height);
                        System.out.println("DEBUG: Creating LANDSCAPE PDF page: " + width + "x" + height);
                    } else {
                        // Portrait orientation
                        pageSize = new PDRectangle(width, height);
                        System.out.println("DEBUG: Creating PORTRAIT PDF page: " + width + "x" + height);
                    }

                    PDPage page = new PDPage(pageSize);
                    pdf.addPage(page);

                    PDImageXObject pdImage = LosslessFactory.createFromImage(pdf, img);
                    try (PDPageContentStream contentStream = new PDPageContentStream(pdf, page)) {
                        contentStream.drawImage(pdImage, 0, 0, width, height);
                    }
                    logger.info("Slide added to PDF successfully");
                    System.out.println("DEBUG: Slide added to PDF");
                } catch (OutOfMemoryError oom) {
                    System.err.println("DEBUG: OutOfMemoryError during slide rendering");
                    oom.printStackTrace();
                    throw new IOException("Out of memory while rendering certificate. Please try again.", oom);
                } catch (Exception e) {
                    System.err.println("DEBUG: Error during slide rendering: " + e.getMessage());
                    e.printStackTrace();
                    throw new IOException("Failed to render slide", e);
                } finally {
                    if (graphics != null) {
                        graphics.dispose();
                    }
                }
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                pdf.save(out);
                logger.info("PDF generation complete, size: {}", out.size());
                System.out.println("DEBUG: PDF generation complete, size: " + out.size());
                return out.toByteArray();
            }
        } catch (IOException e) {
            System.err.println("DEBUG: PDF conversion failed: " + e.getMessage());
            e.printStackTrace();
            logger.error("Failed to generate PDF certificate", e);
            throw e;
        } catch (Exception e) {
            System.err.println("DEBUG: Unexpected error: " + e.getMessage());
            e.printStackTrace();
            logger.error("Unexpected error during PDF generation", e);
            throw new IOException("Failed to generate PDF certificate", e);
        }
    }
}
