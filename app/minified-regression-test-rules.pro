# Test-only Error Prone annotation metadata refers to the Java compiler API (CLASS retention).
# Android does not provide javax.lang.model and no application/test code calls this type.
# Do not propagate this rule to production R8 configuration or suppress javax.* generally.
-dontwarn javax.lang.model.element.Modifier
