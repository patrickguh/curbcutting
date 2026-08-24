package ca.allyscan.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Controller
public class AccountController {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int RESET_CODE_VALID_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScanJobRepository scanJobRepository;
    private final ScanService scanService;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final EmailService emailService;

    public AccountController(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             ScanJobRepository scanJobRepository,
                             ScanService scanService,
                             PasswordResetCodeRepository passwordResetCodeRepository,
                             EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.scanJobRepository = scanJobRepository;
        this.scanService = scanService;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.emailService = emailService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("email", "");
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String password,
                           @RequestParam("confirm-password") String confirmPassword,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        String normalizedEmail = email == null ? "" : email.strip().toLowerCase();

        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
            model.addAttribute("error", "Enter a valid email address.");
            model.addAttribute("email", email);
            return "register";
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            model.addAttribute("error", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            model.addAttribute("email", email);
            return "register";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("email", email);
            return "register";
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            model.addAttribute("error", "An account with that email already exists.");
            model.addAttribute("email", email);
            return "register";
        }

        userRepository.save(new User(normalizedEmail, passwordEncoder.encode(password)));
        redirectAttributes.addFlashAttribute("registered", true);
        return "redirect:/login";
    }

    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        if (signedIn) {
            model.addAttribute("userEmail", authentication.getName());
        }
        return "settings";
    }

    @GetMapping("/account/scans")
    public String myScans(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<ScanJob> jobs = scanJobRepository.findByOwnerId(user.getId());
        jobs.sort(Comparator.comparing(ScanJob::getCreatedAt).reversed());
        model.addAttribute("jobs", jobs);
        model.addAttribute("userEmail", user.getEmail());
        return "my-scans";
    }

    @PostMapping("/account/scans")
    public String submitScan(@RequestParam String url, Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        ScanJob job = scanService.enqueue(url, user);
        redirectAttributes.addFlashAttribute("submittedId", job.getId());
        return "redirect:/account/scans";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, RedirectAttributes redirectAttributes) {
        String normalizedEmail = email == null ? "" : email.strip().toLowerCase();

        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            String code = generateCode();
            passwordResetCodeRepository.save(new PasswordResetCode(
                    user, code, OffsetDateTime.now().plusMinutes(RESET_CODE_VALID_MINUTES)));
            emailService.sendPasswordResetCode(user.getEmail(), code);
        });

        // Same message regardless of whether the account exists, so this can't be used to enumerate accounts.
        redirectAttributes.addFlashAttribute("codeSent", true);
        redirectAttributes.addAttribute("email", normalizedEmail);
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email == null ? "" : email);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String email,
                                @RequestParam String code,
                                @RequestParam String password,
                                @RequestParam("confirm-password") String confirmPassword,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        String normalizedEmail = email == null ? "" : email.strip().toLowerCase();

        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            model.addAttribute("error", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            model.addAttribute("email", email);
            return "reset-password";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("email", email);
            return "reset-password";
        }

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        PasswordResetCode validCode = user == null ? null : passwordResetCodeRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(c -> c.isValid(code))
                .findFirst()
                .orElse(null);

        if (validCode == null) {
            model.addAttribute("error", "That code is invalid or has expired. Request a new one below.");
            model.addAttribute("email", email);
            return "reset-password";
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        validCode.markUsed();
        passwordResetCodeRepository.save(validCode);
        redirectAttributes.addFlashAttribute("passwordReset", true);
        return "redirect:/login";
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
