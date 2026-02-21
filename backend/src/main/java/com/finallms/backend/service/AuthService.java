package com.finallms.backend.service;

import com.finallms.backend.dto.AuthDto;
import com.finallms.backend.entity.User;
import com.finallms.backend.exception.BadRequestException;
import com.finallms.backend.exception.ResourceNotFoundException;
import com.finallms.backend.exception.UnauthorizedException;
import com.finallms.backend.repository.UserRepository;
import com.finallms.backend.util.JwtUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// import java.util.Optional;
import com.finallms.backend.enums.Role;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OtpService otpService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${admin.init.enabled:false}")
    private boolean adminInitEnabled;
    @Value("${admin.init.email:}")
    private String adminInitEmail;
    @Value("${admin.init.password:}")
    private String adminInitPassword;

    private Cache<String, AtomicInteger> adminFailedAttempts = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    private Cache<String, Boolean> adminBlocked = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    // Admin Initialization
    public void initAdmin() {
        System.out.println("DEBUG: initAdmin called. Enabled: " + adminInitEnabled + ", Email: " + adminInitEmail);
        if (!adminInitEnabled)
            return;
        if (adminInitEmail == null || adminInitEmail.isBlank())
            return;
        if (adminInitPassword == null || adminInitPassword.isBlank())
            return;
        User admin = userRepository.findByEmail(adminInitEmail).orElse(null);
        if (admin == null) {
            System.out.println("DEBUG: Admin not found, creating new one.");
            User nu = new User();
            nu.setEmail(adminInitEmail);
            nu.setPassword(passwordEncoder.encode(adminInitPassword));
            nu.setRole(Role.ADMIN);
            nu.setName("Administrator");
            userRepository.save(nu);
            System.out.println("DEBUG: Admin created.");
            return;
        }
        System.out.println("DEBUG: Admin found. Updating if needed.");
        boolean passOk = admin.getPassword() != null && passwordEncoder.matches(adminInitPassword, admin.getPassword());
        if (!passOk) {
            admin.setPassword(passwordEncoder.encode(adminInitPassword));
        }
        if (admin.getRole() != Role.ADMIN) {
            admin.setRole(Role.ADMIN);
        }
        if (admin.getName() == null || admin.getName().isBlank()) {
            admin.setName("Administrator");
        }
        userRepository.save(admin);
    }

    public AuthDto.AuthResponse loginAdmin(AuthDto.LoginRequest request) {
        initAdmin();
        String email = request.getEmail();
        System.out.println("DEBUG: loginAdmin called with email: " + email);
        if (email == null) {
            throw new RuntimeException("Email required");
        }
        String normalizedEmail = email.trim().toLowerCase();
        String normalizedPassword = request.getPassword() != null ? request.getPassword().trim() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> userRepository.findByEmail(email.trim())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found")));

        boolean passwordOk = user.getPassword() != null
                && passwordEncoder.matches(normalizedPassword, user.getPassword());
        if (adminBlocked.getIfPresent(normalizedEmail) != null && !passwordOk) {
            throw new RuntimeException("Too many failed attempts. Try later.");
        }
        if (!passwordOk) {
            AtomicInteger fails = adminFailedAttempts.get(normalizedEmail, k -> new AtomicInteger(0));
            if (fails.incrementAndGet() >= 5) {
                adminBlocked.put(normalizedEmail, Boolean.TRUE);
            }
            throw new RuntimeException("Invalid credentials");
        }

        if (user.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("Unauthorized");
        }

        adminFailedAttempts.invalidate(normalizedEmail);
        adminBlocked.invalidate(normalizedEmail);
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        AuthDto.AuthResponse response = new AuthDto.AuthResponse();
        response.setToken(token);
        response.setRole(user.getRole().name());
        response.setName(user.getName());
        response.setMessage("Login successful");
        return response;
    }

    public String sendOtp(String phone) {
        // Normalize to 10 digits (strip country code) before storing OTP
        String normalized = normalizePhone(phone);
        return otpService.generateAndSendOtp(normalized);
    }

    public java.util.Map<String, Object> getStudentCheck(String phone) {
        String normalizedPhone = normalizePhone(phone);
        User user = userRepository.findByPhone(normalizedPhone).orElse(null);
        if (user == null) {
            return java.util.Map.of("exists", false);
        }
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        String email = user.getEmail();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("exists", true);
        result.put("hasPassword", hasPassword);
        result.put("email", email != null ? email : "");
        return result;
    }

    // Normalize phone: strip country code prefix, keep last 10 digits
    private String normalizePhone(String phone) {
        if (phone == null)
            return "";
        String digits = phone.replaceAll("[^0-9]", "").trim();
        if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits;
    }

    public boolean userExistsByPhone(String phone) {
        return userRepository.findByPhone(normalizePhone(phone)).isPresent();
    }

    public AuthDto.AuthResponse loginStudentWithPassword(AuthDto.LoginRequest request) {
        String email = request.getEmail();
        if (email == null || email.trim().isEmpty()) {
            throw new com.finallms.backend.exception.BadRequestException("Email is required");
        }
        String normalizedEmail = email.trim().toLowerCase();
        String rawPassword = request.getPassword() != null ? request.getPassword().trim() : "";
        if (rawPassword.isEmpty()) {
            throw new com.finallms.backend.exception.BadRequestException("Password is required");
        }
        if (rawPassword.length() < 8) {
            throw new com.finallms.backend.exception.BadRequestException("Password must be at least 8 characters");
        }
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new com.finallms.backend.exception.BadRequestException(
                        "No account found with this email"));
        if (user.getRole() != Role.STUDENT) {
            throw new com.finallms.backend.exception.BadRequestException("Unauthorized access");
        }
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        if (!hasPassword) {
            // First time password setup
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
        } else {
            boolean passwordOk = passwordEncoder.matches(rawPassword, user.getPassword());
            if (!passwordOk) {
                throw new com.finallms.backend.exception.BadRequestException("Wrong password. Please try again.");
            }
        }
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        AuthDto.AuthResponse response = new AuthDto.AuthResponse();
        response.setToken(token);
        response.setRole(user.getRole().name());
        response.setName(user.getName());
        response.setMessage("Login successful");
        return response;
    }

    public String sendEmailOtp(String email) {
        return otpService.generateAndSendEmailOtp(email);
    }

    public boolean userExistsByEmail(String email) {
        if (email == null)
            return false;
        return userRepository.findByEmail(email.trim().toLowerCase()).isPresent();
    }

    public AuthDto.AuthResponse verifyLoginOtp(AuthDto.VerifyOtpRequest request) {
        String phone = normalizePhone(request.getPhone());
        // 1. Validate OTP
        if (!otpService.validateOtp(phone, request.getOtp()) &&
                !otpService.validateOtp(request.getPhone(), request.getOtp())) {
            throw new BadRequestException("Invalid or Expired OTP");
        }

        // 2. Fetch User (MUST EXIST)
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not registered. Please register first."));

        // 3. Generate Token
        String token = jwtUtil.generateToken(user.getPhone(), user.getRole().name());

        AuthDto.AuthResponse response = new AuthDto.AuthResponse();
        response.setToken(token);
        response.setRole(user.getRole().name());
        response.setName(user.getName());
        response.setMessage("Login successful");
        return response;
    }

    public AuthDto.AuthResponse verifyLoginEmailOtp(AuthDto.VerifyEmailOtpRequest request) {
        if (!otpService.validateOtp(request.getEmail(), request.getOtp())) {
            throw new BadRequestException("Invalid or Expired OTP");
        }
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException("User not registered. Please register first."));
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        AuthDto.AuthResponse response = new AuthDto.AuthResponse();
        response.setToken(token);
        response.setRole(user.getRole().name());
        response.setName(user.getName());
        response.setMessage("Login successful");
        return response;
    }

    public AuthDto.AuthResponse verifyRegistrationOtp(AuthDto.VerifyOtpRequest request) {
        // 1. Validate OTP
        if (!otpService.validateOtp(request.getPhone(), request.getOtp())) {
            throw new BadRequestException("Invalid or Expired OTP");
        }

        // 2. Check Exists
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new BadRequestException("User already registered. Please login.");
        }

        // 3. Create User
        User newUser = new User();
        newUser.setPhone(request.getPhone());
        newUser.setRole(Role.STUDENT);
        newUser.setName(request.getName());
        newUser.setEmail(request.getEmail());
        newUser.setAddress(request.getAddress()); // Storing Address

        userRepository.save(newUser);

        // 4. Generate Token
        String token = jwtUtil.generateToken(newUser.getPhone(), newUser.getRole().name());

        AuthDto.AuthResponse response = new AuthDto.AuthResponse();
        response.setToken(token);
        response.setRole(newUser.getRole().name());
        response.setName(newUser.getName());
        response.setMessage("Registration successful");
        return response;
    }

    public AuthDto.AuthResponse verifyRegistrationEmailOtp(AuthDto.VerifyEmailOtpRequest request) {
        if (!otpService.validateOtp(request.getEmail(), request.getOtp())) {
            throw new BadRequestException("Invalid or Expired OTP");
        }
        if (userRepository.findByEmail(request.getEmail().trim().toLowerCase()).isPresent()) {
            throw new BadRequestException("User already registered. Please login.");
        }
        User newUser = new User();
        newUser.setEmail(request.getEmail().trim().toLowerCase());
        newUser.setRole(Role.STUDENT);
        newUser.setName(request.getName());
        newUser.setAddress(request.getAddress());
        userRepository.save(newUser);
        String token = jwtUtil.generateToken(newUser.getEmail(), newUser.getRole().name());
        AuthDto.AuthResponse response = new AuthDto.AuthResponse();
        response.setToken(token);
        response.setRole(newUser.getRole().name());
        response.setName(newUser.getName());
        response.setMessage("Registration successful");
        return response;
    }

    public AuthDto.AuthResponse createGuestUser() {
        // Create a unique guest user
        String guestId = "guest_" + System.currentTimeMillis();
        User guest = new User();
        guest.setName("Guest User");
        guest.setPhone(guestId); // Using ID as phone for uniqueness
        guest.setRole(Role.STUDENT);
        userRepository.save(guest);

        String token = jwtUtil.generateToken(guest.getPhone(), guest.getRole().name());
        AuthDto.AuthResponse response = new AuthDto.AuthResponse();
        response.setToken(token);
        response.setRole("GUEST");
        response.setName("Guest");
        response.setMessage("Guest login successful");
        return response;
    }
}
