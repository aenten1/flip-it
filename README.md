# Flip It

A RuneLite plugin for monitoring item prices, calculating profitable flips, and managing combination item recipes.

## ⚠️ Jagex Compliance
This plugin strictly adheres to the [Old School RuneScape Code of Conduct](https://oldschool.runescape.wiki/w/Code_of_Conduct):
- **No Automation**: Does not interact with the game client or perform actions on behalf of the player.
- **Panel Only**: All information is displayed in a side panel, not overlaid on the game world.
- **Trigger-Based Updates**: Prices only update when the user manually refreshes or opens the Grand Exchange interface (no live polling).
- **No Memory Reading**: Uses official RuneLite APIs and public data sources only.

## Features

- **Manual Watchlist**: Track specific items for flipping opportunities.
- **Combination Calculator**: Calculate profits for crafting items (e.g., attaching claws) considering the 2% GE tax.
- **Price Overrides**: Manually set buy/sell prices to test specific scenarios.
- **Historical Data**: View last 5 high/low prices for tracked items.
- **Profit Thresholds**: Filter flips by minimum profit (50k, 100k, 250k, 500k, 1m+).
- **Import/Export**: Save and load your watchlists and recipes as JSON.

## Installation

### From Source
1. Clone the repository.
2. Build with Gradle: `./gradlew shadowJar`
3. Place `build/libs/osrs-flip-plugin.jar` in your RuneLite plugins folder.

### Pre-built JAR
Download the latest release from the [Releases](../../releases) page.

## Usage

1. Launch RuneLite with the plugin enabled.
2. Open the "Flip It" panel from the side bar.
3. Add items to your watchlist via the search bar.
4. Define combination recipes in the "Combinations" tab.
5. View calculated profits and adjust manual prices as needed.

## Configuration

Configuration is stored locally in `~/.runelite/profiles/<profile>/osrsflip.json`.

See `config.schema.json` for the full schema definition.

## Development

### Prerequisites
- Java 11 or higher
- Gradle 8.10+
- VS Code (recommended) or IntelliJ IDEA

### Building
```bash
./gradlew clean build
```

### Running Tests
```bash
./gradlew test
```

## Contributing

We welcome contributions! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting PRs.

**Note on Combination Recipes**: While users can define custom recipes locally, predefined recipes in the main branch require admin approval.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

If you find this plugin useful, consider supporting development:

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/ace554)

## Disclaimer

This plugin is not affiliated with or endorsed by Jagex. Use at your own risk.