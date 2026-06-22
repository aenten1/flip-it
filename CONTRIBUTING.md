# Contributing to Flip It

Thank you for considering contributing to Flip It! This document outlines the process for contributing code, documentation, and ideas.

## 🛡️ Jagex Compliance First

**All contributions MUST adhere to the Old School RuneScape Code of Conduct.**
- No automation of gameplay actions.
- No overlay rendering on the game world (panel-only UI).
- No live polling of prices (trigger-based updates only).
- No reading of game memory beyond official RuneLite APIs.

PRs that violate these principles will be rejected immediately.

## 🚀 Getting Started

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes following the coding standards below.
4. Run tests: `./gradlew test`
5. Submit a Pull Request.

## 📝 Coding Standards

### Java Style
- Follow Google Java Style Guide.
- Use meaningful variable and method names.
- Keep methods small (< 50 lines preferred).
- Add Javadoc for public methods and classes.

### Test-Driven Development (TDD)
- Write tests BEFORE implementing features (Red-Green-Refactor).
- Aim for >80% code coverage on new features.
- Tests must be deterministic and fast.

### Commit Messages
- Use conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
- Example: `feat: add manual price override dialog`

## 🧩 Feature Guidelines

### Combination Recipes
- **User-Defined**: Users can create custom recipes via the UI (stored locally).
- **Predefined Recipes**: Adding recipes to the default list requires:
  - Admin approval.
  - Verification of item IDs and quantities.
  - Documentation of the crafting process.

### Performance
- Avoid blocking the EDT (Event Dispatch Thread).
- Use async operations for API calls and file I/O.
- Cache frequently accessed data (e.g., item names, prices).

## 🐛 Reporting Bugs

Please include:
- RuneLite version.
- Plugin version.
- Steps to reproduce.
- Expected vs. actual behavior.
- Screenshots/logs if applicable.

## 💡 Feature Requests

Before submitting:
1. Check existing issues to avoid duplicates.
2. Ensure the feature complies with Jagex rules.
3. Describe the use case clearly.

## 📜 Code of Conduct

Be respectful and inclusive. Harassment or toxic behavior will not be tolerated.

## 📄 License

By contributing, you agree that your contributions will be licensed under the MIT License.
