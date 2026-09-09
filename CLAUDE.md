# Shliach Tzibbur — Instructions for Claude

## 1. Core Principles

This is a long-term Android application. Code should be written with the expectation that the application will continue to grow substantially.

**Priorities, in order:**

1. Correctness and reliability
2. Maintainability and extensibility
3. Compatibility with supported devices
4. Simple, clean architecture
5. Small application size and minimal dependencies
6. UI polish

Do not sacrifice architecture, maintainability, or device compatibility merely to make a change faster or with less code.

When requirements conflict, preserve existing functionality unless explicitly instructed otherwise.

---

## 2. Platform and Technology

- Language: **Kotlin**
- UI: **Android Jetpack**
- Follow current Android development best practices appropriate for the project's existing SDK/toolchain.
- Prefer Android/Jetpack APIs over adding third-party libraries.
- Minimize external dependencies.
- Before adding a dependency, determine whether the functionality can reasonably be implemented using existing Android/Jetpack APIs or existing project code.
- Do not introduce a library merely for convenience when a small, maintainable implementation can accomplish the same thing.

Do not migrate the project to a different framework, architecture, language, or major library without explicit approval.

---

## 3. Architecture

Build the application as a collection of **modular, independently understandable components**.

- Separate UI, application logic, data handling, and platform-specific functionality appropriately.
- Avoid putting substantial business logic directly inside Activities, Fragments, Views, or Composables.
- Create proper models and abstractions rather than passing loosely structured data throughout the application.
- Prefer components that can be extended without requiring major rewrites.
- Avoid premature abstraction, but when functionality is clearly intended to grow, design the initial implementation so that future expansion is straightforward.
- Do not duplicate functionality when a reusable component is appropriate.
- Do not create abstractions solely for the sake of abstraction.

**Do not take shortcuts that create technical debt simply to complete the current task faster.**

---

## 4. UI and UX

The application must support both modern touchscreen Android devices and older/non-touch devices.

### Touch devices

Use modern Android/Material design conventions where appropriate.

The UI should be:

- Clean
- Neat
- Intuitive
- Consistent
- Uncluttered
- Appropriate for small screens as well as large screens

### Non-touch / D-pad devices

Non-touch devices are a first-class supported platform, not an afterthought.

Where appropriate:

- Detect whether the device has touchscreen capability.
- Adapt interaction patterns for D-pad/key navigation.
- Ensure every interactive element can be reached and activated without touch.
- Provide obvious visual indication of the currently focused element.
- Use highlighting to indicate D-pad focus.
- Ensure focus order is logical.
- Avoid interactions that require gestures, swipes, long presses, or precise touch unless an equivalent non-touch interaction exists.
- Do not unnecessarily alter the appearance of the UI on touchscreen devices merely to accommodate D-pad devices.

When Android/Material conventions conflict with good D-pad usability, **D-pad usability takes priority on non-touch devices**, while the normal Material interaction remains on touchscreen devices.

### Small screens

Many users have very small displays.

Every screen must be designed to remain usable on small screens.

- Avoid unnecessary padding and oversized controls.
- Do not assume a particular screen resolution or aspect ratio.
- Avoid layouts that depend on large amounts of horizontal space.
- Ensure important controls and information remain accessible when space is limited.
- Test layouts at both small and modern screen sizes when practical.

### Visual style

- Avoid emojis unless explicitly requested.
- Avoid unnecessary decorative elements.
- Avoid "AI-looking" styling.
- Do not add gradients, excessive cards, excessive rounded elements, or decorative UI merely because they are fashionable.
- Prefer functional, restrained, polished design.

---

## 5. Compatibility

This application intentionally supports devices that may be significantly older than current Android phones.

Do not assume:

- A large touchscreen
- Modern hardware
- Google Play Services
- High-resolution displays
- Large amounts of RAM
- Fast CPUs
- Modern Android APIs

When using an API that may not exist on older supported Android versions, provide an appropriate compatibility implementation or guard the functionality.

Do not raise the minimum supported Android version without explicit approval.

---

## 6. Changes and Scope

Before modifying code:

1. Understand the existing implementation.
2. Identify related components and dependencies.
3. Determine whether an existing component should be extended rather than creating a duplicate.
4. Consider compatibility with existing devices and functionality.
5. Make the smallest architectural change that properly solves the problem.

Do not rewrite working code unnecessarily.

Do not replace an existing architecture simply because another architecture is more fashionable.

Do not make unrelated cleanup changes while implementing a feature unless they are necessary for the change.

---

## 7. Version Control

**Git is mandatory.**

Before making any code changes:

1. Check the current Git status.
2. Review the current branch and recent commits.
3. **Create a Git commit containing the current state before modifying anything.**
4. The commit message should briefly describe the pre-change state or serve as a clear checkpoint.

Do not begin modifying files until the pre-change commit has succeeded.

After completing the work:

1. Review the Git diff.
2. Ensure no unrelated changes were introduced.
3. Build/test the application.
4. Commit the completed change with a short, descriptive commit message.

Do not leave the repository with unexplained or accidental modifications.

---

## 8. VERSION.md

Every code or functionality change must be documented in `VERSION.md`.

Use concise, organized bullet points.

Each entry should include:

- Version name
- Version number/code
- Date
- Summary of changes

Version name uses:

`MAJOR.MINOR`

Rules:

- Increment **MINOR** for normal changes, features, fixes, and improvements.
- Increment **MAJOR** for major breaking or architectural changes.
- Increment the version code/number for **every change**.

Do not make a code change without updating `VERSION.md`.

The version information in the project configuration and `VERSION.md` must remain consistent.

---

## 9. Verification

After making changes:

- Build the application.
- Fix compilation errors before considering the task complete.
- Run relevant tests when available.
- Check for Android lint/static-analysis problems when practical.
- Review the final diff.
- Verify that the requested functionality works as intended.
- Consider both touchscreen and non-touch/D-pad behavior for UI changes.
- Consider small-screen behavior for UI changes.

Do not report a task as complete merely because the code was written.

If something could not be tested, explicitly state what was not tested and why.

---

## 10. Dependencies

Keep the dependency footprint small.

Before adding a new dependency, explicitly consider:

- Whether Android/Jetpack already provides the required functionality.
- Whether the project already contains an equivalent dependency.
- APK size impact.
- Runtime overhead.
- Compatibility with older devices.
- Long-term maintenance.

Do not add dependencies automatically.

---

## 11. Error Handling and Reliability

This application should favor predictable behavior over clever behavior.

- Handle expected failures explicitly.
- Do not silently swallow exceptions.
- Do not use broad exception handling merely to prevent crashes.
- Provide useful logging where appropriate.
- Avoid blocking the UI thread.
- Handle lifecycle changes correctly.
- Consider process death, configuration changes, and interrupted operations where relevant.

---

## 12. When Requirements Are Unclear

Do not invent requirements that materially affect architecture or user-visible behavior.

If a decision is:

- Architectural
- Irreversible
- Likely to affect compatibility
- Likely to significantly increase dependencies
- Likely to change existing behavior

ask for clarification before proceeding.

For small implementation details, use reasonable judgment and proceed.

---

## 13. Communication

Keep progress updates concise.

Before making substantial changes, briefly state:

- What you found
- What you intend to change
- Any important architectural consideration

During implementation, provide useful progress updates rather than narrating every individual action.

At completion, summarize:

- What changed
- Files/components affected
- Version change
- Tests/build performed
- Any remaining issues or limitations

Do not claim something was tested, built, or verified if it was not.

---

## 14. General Rule

**Prefer a simple, correct, maintainable implementation over a clever or expedient one.**

**Preserve existing behavior unless the task explicitly requires changing it.**

**Treat small-screen, legacy-device, and non-touch compatibility as core requirements.**

**Build for the future without unnecessarily overengineering the present.**