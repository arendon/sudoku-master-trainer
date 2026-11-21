// Managed by android-cve — transitive dependency security constraints.
// Re-run `android-cve apply` to refresh or clean up once upstream BoMs catch up.
dependencies {
    constraints {
        add("ksp", "com.google.guava:guava:33.7.2-jre") {
            because("Resolves CVE-2020-8908, CVE-2023-2976, CVE-2026-102554")
        }
        add("implementation", "com.squareup.okhttp3:okhttp:4.9.2") {
            because("Resolves CVE-2021-0341")
        }
    }
}
