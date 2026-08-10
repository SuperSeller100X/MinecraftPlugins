# GiftSystem - Minecraft Gifting Plugin

A comprehensive gifting system for Minecraft servers with beautiful GUIs and powerful admin control.

## Features

### Player Features
- **Send Gifts**: Send items to other players with a custom message
- **Gift Inbox**: Beautiful GUI to view and claim received gifts
- **Gift Creation GUI**: Advanced interface for creating multi-item gifts
- **Cooldown System**: Configurable cooldown between sending gifts
- **Offline Gifting**: Send gifts to offline players (they'll receive them when they log in)
- **Message Support**: Add personal messages to your gifts

### Admin Features
- **Clear Gifts**: Remove all pending gifts for any player
- **List Gifts**: View all pending gifts for a player
- **Reload Config**: Reload configuration without restarting
- **Permission Control**: Granular permission system
- **Bypass Permissions**: Admins can bypass cooldowns and limits

## Commands

### Player Commands
| Command | Description | Aliases |
|---------|-------------|---------|
| `/gift <player> [message]` | Send the item in your hand/cursor as a gift | `/sendgift` |
| `/giftgui` | Open the gift creation GUI | `/creategift` |
| `/gifts` | Open your gift inbox | `/receivegift`, `/giftinbox` |

### Admin Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/giftadmin clear <player>` | Clear all gifts for a player | `giftsystem.admin` |
| `/giftadmin list <player>` | List all pending gifts | `giftsystem.admin` |
| `/giftadmin reload` | Reload configuration | `giftsystem.admin` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `giftsystem.use` | Use the gift system | true |
| `giftsystem.admin` | Access admin commands | op |
| `giftsystem.bypass` | Bypass cooldowns and limits | op |

## Configuration

Edit `plugins/GiftSystem/config.yml`:

```yaml
# Enable or disable the gifting system entirely
gifting-enabled: true

# Cooldown in seconds between sending gifts
cooldown-seconds: 60

# Maximum number of items per gift
max-gifts-per-send: 36

# Allow gifting to offline players
allow-offline-gifting: true

# Maximum pending gifts a player can receive
max-pending-gifts: 108

# Auto-delete gifts after X days (-1 to disable)
gift-expiry-days: 30

# GUI Settings
gui-settings:
  title: "&6Your Gifts"
  use-filler: true
  filler-material: GRAY_STAINED_GLASS_PANE
```

## Installation

1. Download the `GiftSystem-1.0.0.jar` file
2. Place it in your server's `plugins/` folder
3. Restart or reload your server
4. Configure the plugin in `plugins/GiftSystem/config.yml`

## Building from Source

Requirements:
- Java 17+
- Maven

```bash
cd GiftSystem
mvn clean package
```

The compiled JAR will be in `target/GiftSystem-1.0.0.jar`

## Usage Guide

### Sending a Simple Gift
1. Hold an item in your main hand or cursor
2. Type `/gift <playername> [optional message]`
3. The item will be sent as a gift!

### Using the Gift GUI
1. Type `/giftgui` to open the creation interface
2. Place items in the middle slots
3. Click the compass to select a recipient
4. Optionally add a message with the book
5. Click the chest to send!

### Receiving Gifts
1. When you receive a gift, you'll get a notification
2. Type `/gifts` to open your gift inbox
3. Click on any gift to claim it
4. Items go directly to your inventory

## Support

For issues or feature requests, please contact the development team.

## License

This plugin is proprietary software. All rights reserved.

---

**Version**: 1.0.0  
**API Version**: 1.20  
**Author**: GiftSystemTeam
