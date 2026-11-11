/**
 * Tầng dùng chung, không chứa nghiệp vụ và không phụ thuộc module nghiệp vụ nào.
 * OPEN để module khác dùng được các sub-package.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.soudan.common;

import org.springframework.modulith.ApplicationModule;
