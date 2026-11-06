package com.shop.controller;

import com.shop.model.PendingApproval;
import com.shop.service.ApprovalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Handles Accept / Reject links sent to the Owner's Gmail.
 * GET only shows a confirmation page (so mail scanners cannot trigger a decision);
 * POST performs the Accept or Reject.
 */
@Controller
@RequestMapping("/owner/approval")
public class OwnerApprovalController {

    private final ApprovalService approvalService;

    public OwnerApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/{token}")
    public String review(@PathVariable("token") String token,
                         @RequestParam(name = "action", defaultValue = "accept") String action,
                         Model model) {
        try {
            PendingApproval request = approvalService.review(token);
            model.addAttribute("token", token);
            model.addAttribute("username", request.getDisplayName());
            model.addAttribute("signup", "SIGNUP".equals(request.getType()));
            model.addAttribute("action", "reject".equals(action) ? "reject" : "accept");
            return "owner/approval-review";
        } catch (IllegalArgumentException ex) {
            return result(model, false, ex.getMessage());
        }
    }

    @PostMapping("/{token}/accept")
    public String accept(@PathVariable("token") String token, Model model) {
        try {
            String type = approvalService.approve(token);
            return result(model, true, "SIGNUP".equals(type)
                    ? "Accepted. The admin account has been created."
                    : "Accepted. The password has been updated.");
        } catch (IllegalArgumentException ex) {
            return result(model, false, ex.getMessage());
        }
    }

    @PostMapping("/{token}/reject")
    public String reject(@PathVariable("token") String token, Model model) {
        try {
            approvalService.reject(token);
            return result(model, true,
                    "Rejected. No account was created and the old password is unchanged.");
        } catch (IllegalArgumentException ex) {
            return result(model, false, ex.getMessage());
        }
    }

    private String result(Model model, boolean success, String message) {
        model.addAttribute("success", success);
        model.addAttribute("message", message);
        return "owner/approval-result";
    }
}