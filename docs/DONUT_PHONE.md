# Donut Phone

A smartphone your character takes out with the vanilla **Open Chat** key. The phone is a 3D model held in first person
(with the player's own arm), and its screen is a small app platform. Package: `com.jfant.specter.phone`.

## Using it

- Press your chat key (whatever it is bound to): the phone comes up and the mouse is freed.
- Press the chat key again (when not typing in a field) for regular chat. `/` still opens chat with a slash.
- `Esc` puts the phone away. `Backspace` or mouse button 4 = back. The bar at the bottom: back, home, recent apps.
- Click the status bar (or scroll up on the home screen) for notifications and quick toggles.
- Module **Donut Phone** (Tools) holds size, position, animation speed, arm on/off, and an alternative open key used only
  when "open with chat key" is off. It is switched on the first time Specter runs with the phone.

## How it fits together

| Piece | Class | Job |
|---|---|---|
| OS | `PhoneOS` | Owns everything below; the only class the game hooks call |
| Screen | `PhoneScreen` | Open while the phone is out (frees the mouse, blocks game keys); forwards input |
| Registry | `core.PhoneApplicationRegistry` | Every app (info + factory); home, Settings and switcher read it |
| Apps | `core.PhoneApplicationManager` | Creates apps lazily, front/back lifecycle, ticks, per-app storage, crash isolation |
| Navigation | `core.PhoneNavigationManager` | Home / app / switcher and the transitions |
| Input | `core.PhoneInputManager` | GUI -> phone points, layered dispatch (shade, bars, widgets, app) |
| Notifications | `core.PhoneNotificationManager` | Shared API, history, badges, banners, per-app prefs, toasts when put away |
| Theme | `core.PhoneThemeManager`, `PhoneTheme`, `Wallpapers` | Design tokens every app uses |
| Animation | `core.PhoneAnimationController` | View-space pose for every frame of taking out / putting away |
| 3D | `render.PhoneRenderer`, `render.PhoneMesh` | Phone body, glowing screen, the arm; projects the glass to GUI space |
| Drawing | `ui.PhoneCanvas`, `ui.PhoneUi`, `ui.Glyph`, `gfx.*` | Point-based canvas and the widget kit (the shared design language) |

Game hooks (mixins): `PhoneChatKeyMixin` (wraps only the chat key's `openChatScreen` call in `Gui.handleKeybinds`),
`PhoneHandsMixin` (replaces the first-person hands while the phone is out), `GameRendererMixin#specter$phoneFrame`
(one animation step per frame at the start of `GameRenderer.extract`, because 26.3 extracts the GUI *before* it renders
the world - the 3D phone and the interface must use the same pose).

The interface is ordinary GUI drawing laid exactly over the 3D glass: at rest the glass faces the camera square on, so
its projection is an axis-aligned rectangle and a translate + uniform scale maps phone points onto it. The interface
only shows once the phone is at rest (the 3D screen shows the same wallpaper texture, so the fade-in is seamless). In
third person or with the HUD hidden there is no 3D phone, so the interface draws a flat phone frame itself.

## Adding an app

1. Extend `api.PhoneApplication` and implement `render(PhoneCanvas c, PhoneUi ui, float mouseX, float mouseY, float delta)`.
2. Describe it: `AppInfo.builder("my_app").name("My App").icon(...).permissions(...).build()`.
3. Register it: `PhoneApps.register(info, MyApp::new)` - or, from another mod, list a `PhoneAppProvider` under the
   `specter:phone` entrypoint in `fabric.mod.json`.

That's all: the phone shows the icon (badges included), opens and closes the app, routes input, keeps its data
(`phone().storage()` -> `specter/phone/apps/<id>.json`), lists its permissions and settings in Settings, and catches any
exception the app throws (that app shows "stopped" with Reopen; nothing else is affected).

Minimal app:

```java
public final class HelloApp extends PhoneApplication {
   private int taps;

   @Override
   public void render(PhoneCanvas c, PhoneUi ui, float mx, float my, float delta) {
      float top = ui.header("Hello", null, null, null);
      c.textCentered("Tapped " + taps + " times", c.width / 2, top + 40, PhoneTheme.BODY, c.theme().text());
      ui.button(30, top + 60, c.width - 60, 26, "Tap", PhoneUi.Style.PRIMARY, () -> taps++);
   }
}
```

Useful bits:
- Lifecycle: `onCreate`, `onOpen`, `onClose`, `onDestroy`, `onRoute(route)` (deep links from notifications or
  `phone().openApp(id, route)`), `tick()` while in front, `backgroundTick()` always (set `.background()` in AppInfo).
- Widgets (`PhoneUi`): `header`, `button`, `iconButton`, `toggle`, `slider`, `segmented`, `row`, `card`, `section`,
  `scroll(key)` + `begin/end`, `field(key)` + `textField`, `spinner`, `empty`, `badge`, `avatar`. Clicks fire on release
  and a press inside a scroll list that turns into a drag scrolls instead.
- Raw input (games): override `mouseClicked/Released/Dragged/Scrolled`, `keyPressed`, `charTyped`; they only see input
  no widget took.
- Icons: `AppIcon.painted(key, r -> { AppIcons.tile(r, top, bottom); AppIcons.glyph(r, shape); })` (vector, sharp at
  any size) or `AppIcon.texture(id)`.
- Settings: return `AppSetting.toggle/choice/number(...)` from `settings()`; read with `setting.get(phone())`.
- Notifications: `phone().notify(title, text, route)` (needs `AppPermission.NOTIFICATIONS`).
- Permissions are declared; context methods that need one refuse apps that didn't declare it. Java isn't sandboxed, so
  this keeps honest apps honest.
- Mini-games for Donut Games: implement `apps.games.PhoneGame`, then `GameRegistry.register(info, MyGame::new)`.
- DonutTravel destinations: `Destinations.register(new Destination(...))` with a recipe over Specter's visual modules
  (every change is recorded and undone by "Return home", also after a restart).

## Built-in apps

DonutGram, DonutTravel, Maps, Games (Snake, Minesweeper, Tic-Tac-Toe, Reaction, Memory), Specter (modules, settings,
HUD, client preferences - the real Specter systems, nothing duplicated), Profile, Settings, Chat.

## DonutGram backend

Lives in the Specter+ licence server Worker (`~/SpecterPlus/server`, `src/gram.js`, `src/gram-d1.js`,
`migrations/0003_donutgram.sql`, tests `test/gram.test.js`).

- Sign-in: the game signs a one-time challenge with its Mojang profile key (`donutgram-login:<challenge>:<uuid>`); the
  server checks Mojang's signature on that key. No password or session token is sent. Sessions are HMAC tokens under
  their own label, separate from Specter+ licence tokens.
- Posting: one every 5 minutes per account (GRAM_UPLOAD_SECONDS in wrangler.toml), JPEG/PNG only, 1 MB, 64-2560 px, read from the file header; posts expire after
  24 hours (hourly cron deletes them and their Discord message). Only Donut Phone posts (`X-Specter-Client` header - a
  label, not a lock; the account check and limits are what is enforced).
- Discord is view-only: a channel webhook (`GRAM_DISCORD_WEBHOOK` secret) gets each post with name, picture and time.
- Likes once per account, never on your own post; 3 reports hide a post until an admin restores or removes it.
- Monthly rewards on the 1st (UTC): top 3 by likes received that month - a 30-day locked Specter+ key for non-members
  (shown to the winner in DonutGram > Profile), or the Donut money prize (recorded as pending; the owner pays it in game
  and marks it paid) for members. All numbers configurable with `/admin/gram/config`.
- Admin (Bearer ADMIN_TOKEN, POST): `/admin/gram/config`, `/admin/gram/rewards/list`, `/admin/gram/rewards/paid`,
  `/admin/gram/rewards/run`, `/admin/gram/posts/remove`, `/admin/gram/posts/restore`, `/admin/gram/ban`.
