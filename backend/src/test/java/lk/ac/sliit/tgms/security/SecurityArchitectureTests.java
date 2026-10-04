package lk.ac.sliit.tgms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import lk.ac.sliit.tgms.auth.AuthController;
import lk.ac.sliit.tgms.authorization.RoleGuards;
import lk.ac.sliit.tgms.notification.NotificationController;
import lk.ac.sliit.tgms.profile.UserProfileController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

class SecurityArchitectureTests {

    private static final Map<Class<?>, Set<String>> REVIEWED_UNGUARDED_SELF_SERVICE = Map.of(
            AuthController.class,
                    Set.of(
                            "register",
                            "login",
                            "logout",
                            "requestPasswordReset",
                            "confirmPasswordReset",
                            "resendEmailVerification",
                            "confirmEmailVerification"),
            UserProfileController.class,
                    Set.of("updateProfile"),
            NotificationController.class,
                    Set.of("markRead", "markAllRead"));

    @Test
    void everyMutationIsRoleGuardedOrAnExplicitlyReviewedSelfServiceBoundary()
            throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (var beanDefinition : scanner.findCandidateComponents("lk.ac.sliit.tgms")) {
            Class<?> controller = Class.forName(beanDefinition.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                if (!isMutation(method)) {
                    continue;
                }
                boolean guarded = hasRoleGuard(controller) || hasRoleGuard(method);
                boolean reviewedSelfService = REVIEWED_UNGUARDED_SELF_SERVICE
                        .getOrDefault(controller, Set.of())
                        .contains(method.getName());
                assertThat(guarded || reviewedSelfService)
                        .as("%s#%s must have a RoleGuards annotation or be explicitly reviewed self-service",
                                controller.getSimpleName(), method.getName())
                        .isTrue();
            }
        }
    }

    private boolean isMutation(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    private boolean hasRoleGuard(AnnotatedElement element) {
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType().getEnclosingClass() == RoleGuards.class) {
                return true;
            }
        }
        return false;
    }
}
