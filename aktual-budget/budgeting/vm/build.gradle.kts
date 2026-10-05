plugins { id("aktual.module.viewmodel") }

kotlin {
  commonMainDependencies {
    api(project(":aktual-budget:budgeting:domain"))
  }

  commonTestDependencies {
    implementation(project(":aktual-test"))
  }
}
