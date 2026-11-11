package com.soudan;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void should_haveValidModuleStructure_when_modulesVerified() {
		ApplicationModules.of(Application.class).verify();
	}

}
