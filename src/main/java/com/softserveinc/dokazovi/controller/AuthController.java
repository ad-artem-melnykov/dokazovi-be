package com.softserveinc.dokazovi.controller;

import com.softserveinc.dokazovi.entity.UserEntity;
import com.softserveinc.dokazovi.dto.payload.ApiResponse;
import com.softserveinc.dokazovi.dto.payload.AuthResponse;
import com.softserveinc.dokazovi.dto.payload.LoginRequest;
import com.softserveinc.dokazovi.dto.payload.SignUpRequest;
import com.softserveinc.dokazovi.exception.BadRequestException;
import com.softserveinc.dokazovi.security.TokenProvider;
import com.softserveinc.dokazovi.service.ProviderService;
import com.softserveinc.dokazovi.service.UserService;
import com.softserveinc.dokazovi.util.MailSenderUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.mail.MessagingException;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URI;

import static com.softserveinc.dokazovi.controller.EndPoints.AUTH;
import static com.softserveinc.dokazovi.controller.EndPoints.AUTH_LOGIN;
import static com.softserveinc.dokazovi.controller.EndPoints.AUTH_SIGNUP;
import static com.softserveinc.dokazovi.controller.EndPoints.AUTH_VERIFICATION;

@RestController
@RequestMapping(AUTH)
@RequiredArgsConstructor
public class AuthController {


	private final AuthenticationManager authenticationManager;
	private final TokenProvider tokenProvider;
	private final MailSenderUtil mailSenderUtil;
	private final UserService userService;
	private final ProviderService providerService;

	@PostMapping(AUTH_LOGIN)
	public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(
						loginRequest.getEmail(),
						loginRequest.getPassword()
				)
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		String token = tokenProvider.createToken(authentication);
		UserEntity userEntity = userService.findByEmail(loginRequest.getEmail());
		if (!userEntity.getEnabled()) {
			throw new BadRequestException("Please confirm your email!");
		} else {
			AuthResponse authResponse = new AuthResponse(token);
			authResponse.setAccessToken(token);
			return ResponseEntity.ok(authResponse);
		}
	}

	@PostMapping(AUTH_SIGNUP)
	public ResponseEntity<ApiResponse> registerUser(@Valid @RequestBody SignUpRequest signUpRequest)
			throws IOException, MessagingException {
		if (providerService.existsByLocalEmail(signUpRequest.getEmail())) {
			throw new BadRequestException("Email address already in use.");
		}
		UserEntity user = userService.registerNewUser(signUpRequest);
		providerService.createLocalProviderEntityForUser(user, signUpRequest.getEmail());
		URI location = ServletUriComponentsBuilder
				.fromCurrentContextPath().path("/api/user/me")
				.buildAndExpand(user.getId()).toUri();
		mailSenderUtil.sendMessage(user);
		return ResponseEntity.created(location)
				.body(new ApiResponse(true, "User registered successfully! Please confirm your email!"));
	}

	@GetMapping(AUTH_VERIFICATION)
	public ResponseEntity<ApiResponse> registrationComplete(
			@RequestParam(value = "token") String token) {
		userService.setEnableTrue(userService.getVerificationToken(token).getUser());
		return ResponseEntity.ok().body(new ApiResponse(true, "Email confirmed! redirect to login page!"));
	}
}
