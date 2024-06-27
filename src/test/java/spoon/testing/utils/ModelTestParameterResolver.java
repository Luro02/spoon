package spoon.testing.utils;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.ModelConsistencyChecker;

import java.lang.reflect.Executable;
import java.util.stream.Collectors;

public class ModelTestParameterResolver implements ParameterResolver {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create("spoon", "modeltest");

	@Override
	public boolean supportsParameter(
		ParameterContext parameterContext,
		ExtensionContext extensionContext
	) throws ParameterResolutionException {
		if (!parameterContext.getDeclaringExecutable().isAnnotationPresent(ModelTest.class)) {
			return false;
		}
		Class<?> type = parameterContext.getParameter().getType();
		return type == Launcher.class || type == CtModel.class || type == Factory.class;
	}

	@Override
	public Object resolveParameter(
		ParameterContext parameterContext,
		ExtensionContext extensionContext
	) throws ParameterResolutionException {
		Executable method = parameterContext.getDeclaringExecutable();

		Launcher launcher = (Launcher) extensionContext.getStore(NAMESPACE)
			.getOrComputeIfAbsent(method, this::createLauncher);

		if (parameterContext.getParameter().getType() == Launcher.class) {
			return launcher;
		} else if (parameterContext.getParameter().getType() == CtModel.class) {
			return launcher.getModel();
		} else if (parameterContext.getParameter().getType() == Factory.class) {
			return launcher.getFactory();
		}

		throw new AssertionError("supportsParameter is not exhaustive");
	}

	private Launcher createLauncher(Executable method) {
		ModelTest annotation = method.getAnnotation(ModelTest.class);

		Launcher launcher = new Launcher();
		if (annotation.complianceLevel() > 0) {
			launcher.getEnvironment().setComplianceLevel(annotation.complianceLevel());
		}
		launcher.getEnvironment().setCommentEnabled(annotation.commentsEnabled());
		launcher.getEnvironment().setAutoImports(annotation.autoImport());
		launcher.getEnvironment().setNoClasspath(annotation.noClasspath());
		for (String path : annotation.value()) {
			launcher.addInputResource(path);
		}
		launcher.buildModel();

		// ensure that the model is valid
		launcher.getModel().getAllModules().forEach(ctModule -> {
			var invalidElements = ModelConsistencyChecker.listInconsistencies(ctModule);

			if (!invalidElements.isEmpty()) {
				throw new IllegalStateException("Model is inconsistent for %s, %d elements have invalid parents:%n%s".formatted(
					method.getName(),
					invalidElements.size(),
					invalidElements.stream()
						.map(ModelConsistencyChecker.InconsistentElements::toString)
						.limit(5)
						.collect(Collectors.joining(System.lineSeparator()))
				));
			}
		});

		return launcher;
	}
}
