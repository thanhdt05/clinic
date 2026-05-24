package com.project.clinic.controller;

import com.project.clinic.dto.ChangePasswordDTO;
import com.project.clinic.dto.ProfileResponseDTO;
import com.project.clinic.entity.Account;
import com.project.clinic.entity.Doctor;
import com.project.clinic.mapper.DoctorMapper;
import com.project.clinic.mapper.EmployeeMapper;
import com.project.clinic.security.ClinicPrincipal;
import com.project.clinic.service.AccountService;
import com.project.clinic.service.DoctorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private static final String AVATAR_UPLOAD_URL_PREFIX = "/uploads/avatars";
    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private final AccountService accountService;
    private final DoctorService doctorService;
    private final PasswordEncoder passwords;
    private final Path avatarStorageDirectory;

    @Autowired
    public ProfileController(
            AccountService accountService,
            DoctorService doctorService,
            PasswordEncoder passwords,
            @Value("${app.upload.avatar-dir:uploads/avatars}") String avatarUploadDir
    ) {
        this.accountService = accountService;
        this.doctorService = doctorService;
        this.passwords = passwords;
        this.avatarStorageDirectory = Paths.get(avatarUploadDir).toAbsolutePath().normalize();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal ClinicPrincipal principal,
                                         @RequestParam(required = false) Integer accountId) {
        int userId = profileAccountId(principal, accountId);
        Account account = accountService.findById(userId);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy người dùng");
        }

        if (account.getRole() == Account.Role.DOCTOR) {
            Doctor doctor = doctorService.findByAccountId(userId).orElse(null);
            return ResponseEntity.ok(DoctorMapper.toDoctorResponse(account, doctor));
        }

        return ResponseEntity.ok(EmployeeMapper.toEmployeeList(Collections.singletonList(account)).getFirst());
    }

    @PutMapping("/profile")
    @Transactional
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal ClinicPrincipal principal,
            @RequestParam(required = false) Integer accountId,
            @RequestBody ProfileResponseDTO request
    ) {
        int userId = profileAccountId(principal, accountId);
        Account account = accountService.findById(userId);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy người dùng");
        }

        if (request.getFullName() != null) {
            account.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            account.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            account.setEmail(request.getEmail());
        }
        if (request.getAvatarUrl() != null) {
            account.setAvatarUrl(request.getAvatarUrl());
        }

        accountService.save(account);

        if (account.getRole() == Account.Role.DOCTOR) {
            Doctor doctor = doctorService.findByAccountId(userId).orElse(new Doctor());

            doctor.setAccount(account);

            if (request.getSpecialty() != null) {
                doctor.setSpecialty(request.getSpecialty());
            }
            if (request.getDegree() != null) {
                doctor.setDegree(request.getDegree());
            }
            if (request.getBio() != null) {
                doctor.setBio(request.getBio());
            }

            doctorService.save(doctor);
            return ResponseEntity.ok(DoctorMapper.toDoctorResponse(account, doctor));
        }

        return ResponseEntity.ok(EmployeeMapper.toEmployeeList(Collections.singletonList(account)).getFirst());
    }

    @PutMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal ClinicPrincipal principal,
            @RequestBody ChangePasswordDTO request
    ) {
        Account account = accountService.findById(principal.accountId());
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy người dùng");
        }

        if (!passwords.matches(request.getCurrentPassword(), account.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Mật khẩu hiện tại không chính xác!");
        }

        account.setPassword(passwords.encode(request.getNewPassword()));
        accountService.save(account);

        return ResponseEntity.ok("Đổi mật khẩu thành công!");
    }

    @PostMapping("/upload-avatar")
    @Transactional
    public ResponseEntity<?> uploadAvatar(
            @AuthenticationPrincipal ClinicPrincipal principal,
            @RequestParam(required = false) Integer accountId,
            @RequestParam("file") MultipartFile file
    ) {
        Account account = accountService.findById(profileAccountId(principal, accountId));
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy người dùng");
        }

        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Vui lòng chọn file ảnh!");
        }

        if (!isSupportedImageType(file)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Chỉ chấp nhận JPG, PNG, GIF hoặc WEBP!");
        }

        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Kích thước ảnh tối đa là 2MB!");
        }

        String previousAvatarUrl = account.getAvatarUrl();
        String storedFileName = null;

        try {
            Files.createDirectories(avatarStorageDirectory);
            storedFileName = storeAvatar(file);
            String avatarUrl = buildAvatarUrl(storedFileName);

            account.setAvatarUrl(avatarUrl);
            accountService.save(account);
            deleteStoredAvatarIfManaged(previousAvatarUrl);

            Map<String, String> response = new LinkedHashMap<>();
            response.put("avatarUrl", avatarUrl);
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            deleteStoredAvatarByFileName(storedFileName);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Không thể lưu ảnh đại diện lúc này!");
        }
    }

    private int profileAccountId(ClinicPrincipal principal, Integer accountId) {
        if (accountId == null || accountId == principal.accountId()) {
            return principal.accountId();
        }
        if (principal.role() != Account.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return accountId;
    }

    private boolean isSupportedImageType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return false;
        }

        return ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    private String storeAvatar(MultipartFile file) throws IOException {
        String extension = resolveFileExtension(file);
        String storedFileName = UUID.randomUUID() + "." + extension;
        Path targetFile = avatarStorageDirectory.resolve(storedFileName).normalize();

        if (!targetFile.startsWith(avatarStorageDirectory)) {
            throw new IOException("Đường dẫn lưu trữ không hợp lệ!");
        }

        Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        return storedFileName;
    }

    private String resolveFileExtension(MultipartFile file) {
        return switch (file.getContentType().toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private String buildAvatarUrl(String storedFileName) {
        return AVATAR_UPLOAD_URL_PREFIX + "/" + storedFileName;
    }

    private void deleteStoredAvatarIfManaged(String avatarUrl) {
        resolveStoredAvatarPath(avatarUrl).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        });
    }

    private void deleteStoredAvatarByFileName(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            return;
        }

        Path storedFilePath = avatarStorageDirectory.resolve(storedFileName).normalize();
        if (!storedFilePath.startsWith(avatarStorageDirectory)) {
            return;
        }

        try {
            Files.deleteIfExists(storedFilePath);
        } catch (IOException ignored) {
        }
    }

    private Optional<Path> resolveStoredAvatarPath(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return Optional.empty();
        }

        try {
            URI avatarUri = URI.create(avatarUrl);
            String avatarPath = avatarUri.getPath();
            if (!StringUtils.hasText(avatarPath) || !avatarPath.startsWith(AVATAR_UPLOAD_URL_PREFIX + "/")) {
                return Optional.empty();
            }

            String fileName = Paths.get(avatarPath).getFileName().toString();
            if (!StringUtils.hasText(fileName)) {
                return Optional.empty();
            }

            Path resolvedPath = avatarStorageDirectory.resolve(fileName).normalize();
            if (!resolvedPath.startsWith(avatarStorageDirectory)) {
                return Optional.empty();
            }

            return Optional.of(resolvedPath);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
