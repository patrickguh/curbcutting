package ca.curbcutting.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;

@Controller
public class AccountController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScanJobRepository scanJobRepository;
    private final ScanService scanService;

    public AccountController(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             ScanJobRepository scanJobRepository,
                             ScanService scanService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.scanJobRepository = scanJobRepository;
        this.scanService = scanService;
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
}
