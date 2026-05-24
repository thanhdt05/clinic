package com.project.clinic.controller;

import com.project.clinic.dto.ShiftRequestDTO;
import com.project.clinic.dto.ShiftResponseDTO;
import com.project.clinic.entity.Account;
import com.project.clinic.entity.Shift;
import com.project.clinic.mapper.ShiftMapper;
import com.project.clinic.security.ClinicPrincipal;
import com.project.clinic.service.ShiftService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {
    private final ShiftService shiftService;

    @Autowired
    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    // lấy ca  trực
    @GetMapping
    public ResponseEntity<List<ShiftResponseDTO>> getShifts(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                            @AuthenticationPrincipal ClinicPrincipal principal) {
        Account.Role role = principal.role();
        int userId = principal.accountId();
        List<Shift> shifts = role == Account.Role.DOCTOR
                ? shiftService.findShiftsByDoctorBetween(userId, startDate, endDate)
                : shiftService.findShiftsBetween(startDate, endDate);
        return ResponseEntity.ok(ShiftMapper.toShiftResponseList(shifts, role, userId));
    }

    // lấy chi tiết 1 ca trực
    @GetMapping("/{id}")
    public ResponseEntity<?> getShiftById(
            @PathVariable int id, @AuthenticationPrincipal ClinicPrincipal principal) {
        Account.Role role = principal.role();
        int userId = principal.accountId();

        try {
            Shift shift = shiftService.findById(id);
            ShiftResponseDTO dto = ShiftMapper.toShiftResponse(shift, role, userId);

            if (role == Account.Role.DOCTOR && (dto.getRooms() == null || dto.getRooms().isEmpty())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Bạn không có quyền xem ca trực này");
            }

            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy ca trực");
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createShift(@RequestBody ShiftRequestDTO request) {
        try {
            shiftService.createShift(request);
            return ResponseEntity.status(HttpStatus.CREATED).body("Tạo ca trực thành công");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Lỗi khi tạo ca trực: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateShift(
            @PathVariable int id, @RequestBody ShiftRequestDTO request) {
        try {
            shiftService.updateShift(id, request);
            return ResponseEntity.ok("Cập nhật ca trực thành công");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteShift(
            @PathVariable int id, @RequestParam(defaultValue = "false") boolean deleteFuture) {
        try {
            shiftService.deleteShift(id, deleteFuture);
            return ResponseEntity.ok("Xóa ca trực thành công");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
