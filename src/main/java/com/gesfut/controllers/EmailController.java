package com.gesfut.controllers;
import com.gesfut.dtos.requests.EmailRequest;
import com.gesfut.services.impl.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    @Autowired
    private EmailService emailService;

    // Auditoria 22/10: estaba abierto sin autenticacion (relay de mails). Solo ADMIN.
    @PostMapping("/send")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ADMIN')")
    public String sendEmail(@RequestBody EmailRequest request) {
        emailService.sendEmail(request.getTo(), request.getSubject(), request.getText());
        return "Correo enviado exitosamente";
    }
}