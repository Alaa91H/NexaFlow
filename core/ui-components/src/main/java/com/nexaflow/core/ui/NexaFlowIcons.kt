package com.nexaflow.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QueryBuilder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon entry for the picker: a stable [name] (persisted in automations), the
 * [icon] vector, a [category] for filtering and search [keywords].
 */
data class NexaFlowIconEntry(
    val name: String,
    val icon: ImageVector,
    val category: String,
    val keywords: String
)

object NexaFlowIcons {

    const val CATEGORY_GENERAL = "general"
    const val CATEGORY_CONNECTIVITY = "connectivity"
    const val CATEGORY_SOUND = "sound"
    const val CATEGORY_DISPLAY = "display"
    const val CATEGORY_MEDIA = "media"
    const val CATEGORY_SYSTEM = "system"
    const val CATEGORY_BATTERY = "battery"
    const val CATEGORY_APPS = "apps"
    const val CATEGORY_SECURITY = "security"
    const val CATEGORY_TIME = "time"
    const val CATEGORY_LOCATION = "location"

    /** All selectable icons, grouped logically by category. */
    val entries: List<NexaFlowIconEntry> = listOf(
        // ── General ────────────────────────────────────────────────
        NexaFlowIconEntry("bolt", Icons.Filled.Bolt, CATEGORY_GENERAL, "lightning fast zap energy power"),
        NexaFlowIconEntry("star", Icons.Filled.Star, CATEGORY_GENERAL, "favorite rating highlight"),
        NexaFlowIconEntry("star_border", Icons.Filled.StarBorder, CATEGORY_GENERAL, "favorite rating outline"),
        NexaFlowIconEntry("favorite", Icons.Filled.Favorite, CATEGORY_GENERAL, "heart like love"),
        NexaFlowIconEntry("favorite_border", Icons.Filled.FavoriteBorder, CATEGORY_GENERAL, "heart like love outline"),
        NexaFlowIconEntry("check", Icons.Filled.Check, CATEGORY_GENERAL, "done confirm yes ok"),
        NexaFlowIconEntry("check_circle", Icons.Filled.CheckCircle, CATEGORY_GENERAL, "done success complete"),
        NexaFlowIconEntry("check_circle_outline", Icons.Filled.CheckCircleOutline, CATEGORY_GENERAL, "done success outline"),
        NexaFlowIconEntry("close", Icons.Filled.Close, CATEGORY_GENERAL, "cancel x dismiss"),
        NexaFlowIconEntry("add", Icons.Filled.Add, CATEGORY_GENERAL, "plus new create"),
        NexaFlowIconEntry("add_circle", Icons.Filled.AddCircle, CATEGORY_GENERAL, "plus new create"),
        NexaFlowIconEntry("add_circle_outline", Icons.Filled.AddCircleOutline, CATEGORY_GENERAL, "plus new create outline"),
        NexaFlowIconEntry("settings", Icons.Filled.Settings, CATEGORY_GENERAL, "gear configure options"),
        NexaFlowIconEntry("search", Icons.Filled.Search, CATEGORY_GENERAL, "find magnifier explore"),
        NexaFlowIconEntry("edit", Icons.Filled.Edit, CATEGORY_GENERAL, "pencil modify change"),
        NexaFlowIconEntry("delete", Icons.Filled.Delete, CATEGORY_GENERAL, "trash remove erase"),
        NexaFlowIconEntry("share", Icons.Filled.Share, CATEGORY_GENERAL, "send export connect"),
        NexaFlowIconEntry("refresh", Icons.Filled.Refresh, CATEGORY_GENERAL, "reload sync retry"),
        NexaFlowIconEntry("more_vert", Icons.Filled.MoreVert, CATEGORY_GENERAL, "menu dots vertical"),
        NexaFlowIconEntry("more_horiz", Icons.Filled.MoreHoriz, CATEGORY_GENERAL, "menu dots horizontal"),
        NexaFlowIconEntry("info", Icons.Filled.Info, CATEGORY_GENERAL, "help about details"),
        NexaFlowIconEntry("help_outline", Icons.AutoMirrored.Filled.HelpOutline, CATEGORY_GENERAL, "question support faq"),
        NexaFlowIconEntry("warning", Icons.Filled.Warning, CATEGORY_GENERAL, "alert caution danger"),
        NexaFlowIconEntry("grade", Icons.Filled.Grade, CATEGORY_GENERAL, "award top best"),
        NexaFlowIconEntry("flag", Icons.Filled.Flag, CATEGORY_GENERAL, "marker target goal"),
        NexaFlowIconEntry("face", Icons.Filled.Face, CATEGORY_GENERAL, "smile emoji avatar"),
        NexaFlowIconEntry("person", Icons.Filled.Person, CATEGORY_GENERAL, "user profile account"),
        NexaFlowIconEntry("group", Icons.Filled.Group, CATEGORY_GENERAL, "people team users"),
        NexaFlowIconEntry("language", Icons.Filled.Language, CATEGORY_GENERAL, "globe world translate"),
        NexaFlowIconEntry("public", Icons.Filled.Public, CATEGORY_GENERAL, "globe world earth"),
        NexaFlowIconEntry("work", Icons.Filled.Work, CATEGORY_GENERAL, "briefcase job career"),
        NexaFlowIconEntry("school", Icons.Filled.School, CATEGORY_GENERAL, "education learn study"),
        NexaFlowIconEntry("science", Icons.Filled.Science, CATEGORY_GENERAL, "lab flask experiment"),
        NexaFlowIconEntry("assessment", Icons.Filled.Assessment, CATEGORY_GENERAL, "chart report analytics"),
        NexaFlowIconEntry("dashboard", Icons.Filled.Dashboard, CATEGORY_GENERAL, "home grid panels"),
        NexaFlowIconEntry("widgets", Icons.Filled.Widgets, CATEGORY_GENERAL, "blocks modules tiles"),
        NexaFlowIconEntry("grid_view", Icons.Filled.GridView, CATEGORY_GENERAL, "apps squares layout"),
        NexaFlowIconEntry("email", Icons.Filled.Email, CATEGORY_GENERAL, "mail message inbox"),
        NexaFlowIconEntry("folder", Icons.Filled.Folder, CATEGORY_GENERAL, "directory files"),
        NexaFlowIconEntry("bookmark", Icons.Filled.Bookmark, CATEGORY_GENERAL, "save favorite tag"),
        NexaFlowIconEntry("feedback", Icons.Filled.Feedback, CATEGORY_GENERAL, "comment review rate"),

        // ── Connectivity ───────────────────────────────────────────
        NexaFlowIconEntry("wifi", Icons.Filled.Wifi, CATEGORY_CONNECTIVITY, "internet network wireless"),
        NexaFlowIconEntry("bluetooth", Icons.Filled.Bluetooth, CATEGORY_CONNECTIVITY, "wireless earbuds headset"),
        NexaFlowIconEntry("nfc", Icons.Filled.Nfc, CATEGORY_CONNECTIVITY, "contactless tap payment"),
        NexaFlowIconEntry("airplane", Icons.Filled.AirplanemodeActive, CATEGORY_CONNECTIVITY, "flight mode airplane"),
        NexaFlowIconEntry("flight", Icons.Filled.Flight, CATEGORY_CONNECTIVITY, "plane airport travel"),
        NexaFlowIconEntry("hotspot", Icons.Filled.WifiTethering, CATEGORY_CONNECTIVITY, "tethering share network"),
        NexaFlowIconEntry("mobile_data", Icons.Filled.SignalCellularAlt, CATEGORY_CONNECTIVITY, "cellular signal bars"),
        NexaFlowIconEntry("vpn", Icons.Filled.VpnLock, CATEGORY_CONNECTIVITY, "network lock secure"),
        NexaFlowIconEntry("usb", Icons.Filled.Usb, CATEGORY_CONNECTIVITY, "cable port plug"),
        NexaFlowIconEntry("cast", Icons.Filled.Cast, CATEGORY_CONNECTIVITY, "screen mirror display"),
        NexaFlowIconEntry("rss", Icons.Filled.RssFeed, CATEGORY_CONNECTIVITY, "feed broadcast wifi"),
        NexaFlowIconEntry("data_usage", Icons.Filled.DataUsage, CATEGORY_CONNECTIVITY, "network chart traffic"),
        NexaFlowIconEntry("cloud", Icons.Filled.Cloud, CATEGORY_CONNECTIVITY, "sync online storage"),
        NexaFlowIconEntry("sync", Icons.Filled.Sync, CATEGORY_CONNECTIVITY, "refresh update loop"),
        NexaFlowIconEntry("download", Icons.Filled.Download, CATEGORY_CONNECTIVITY, "save get import"),
        NexaFlowIconEntry("upload", Icons.Filled.Upload, CATEGORY_CONNECTIVITY, "send export share"),
        NexaFlowIconEntry("call", Icons.Filled.Call, CATEGORY_CONNECTIVITY, "phone dial talk"),
        NexaFlowIconEntry("phone", Icons.Filled.Phone, CATEGORY_CONNECTIVITY, "dial call talk"),
        NexaFlowIconEntry("phone_android", Icons.Filled.PhoneAndroid, CATEGORY_CONNECTIVITY, "smartphone device mobile"),
        NexaFlowIconEntry("devices", Icons.Filled.Devices, CATEGORY_CONNECTIVITY, "computer tablet phone"),
        NexaFlowIconEntry("android", Icons.Filled.Android, CATEGORY_CONNECTIVITY, "robot phone device"),

        // ── Sound ──────────────────────────────────────────────────
        NexaFlowIconEntry("volume", Icons.AutoMirrored.Filled.VolumeUp, CATEGORY_SOUND, "loud speaker audio"),
        NexaFlowIconEntry("volume_down", Icons.AutoMirrored.Filled.VolumeDown, CATEGORY_SOUND, "quiet lower audio"),
        NexaFlowIconEntry("volume_mute", Icons.AutoMirrored.Filled.VolumeMute, CATEGORY_SOUND, "silent no sound"),
        NexaFlowIconEntry("volume_off", Icons.AutoMirrored.Filled.VolumeOff, CATEGORY_SOUND, "muted off audio"),
        NexaFlowIconEntry("dnd", Icons.Filled.DoNotDisturb, CATEGORY_SOUND, "silent quiet do not disturb"),
        NexaFlowIconEntry("ring_volume", Icons.Filled.RingVolume, CATEGORY_SOUND, "ringtone ringer alert"),
        NexaFlowIconEntry("music", Icons.Filled.MusicNote, CATEGORY_SOUND, "song melody audio"),
        NexaFlowIconEntry("audiotrack", Icons.Filled.Audiotrack, CATEGORY_SOUND, "song music note"),
        NexaFlowIconEntry("headphones", Icons.Filled.Headphones, CATEGORY_SOUND, "headset listen earphones"),
        NexaFlowIconEntry("speaker", Icons.Filled.Speaker, CATEGORY_SOUND, "sound device audio"),
        NexaFlowIconEntry("mic", Icons.Filled.Mic, CATEGORY_SOUND, "microphone voice record"),
        NexaFlowIconEntry("graphic_eq", Icons.Filled.GraphicEq, CATEGORY_SOUND, "equalizer bars audio"),
        NexaFlowIconEntry("radio", Icons.Filled.Radio, CATEGORY_SOUND, "fm tuner broadcast"),
        NexaFlowIconEntry("message", Icons.AutoMirrored.Filled.Message, CATEGORY_SOUND, "chat bubble sms"),
        NexaFlowIconEntry("sms", Icons.Filled.Sms, CATEGORY_SOUND, "chat text message"),
        NexaFlowIconEntry("send", Icons.AutoMirrored.Filled.Send, CATEGORY_SOUND, "deliver dispatch message"),

        // ── Display ────────────────────────────────────────────────
        NexaFlowIconEntry("sunny", Icons.Filled.WbSunny, CATEGORY_DISPLAY, "light brightness day"),
        NexaFlowIconEntry("dark", Icons.Filled.DarkMode, CATEGORY_DISPLAY, "night moon dark mode"),
        NexaFlowIconEntry("light", Icons.Filled.LightMode, CATEGORY_DISPLAY, "sun day bright light mode"),
        NexaFlowIconEntry("nightlight", Icons.Filled.Nightlight, CATEGORY_DISPLAY, "night moon sleep"),
        NexaFlowIconEntry("brightness", Icons.Filled.BrightnessMedium, CATEGORY_DISPLAY, "screen light level"),
        NexaFlowIconEntry("brightness_auto", Icons.Filled.BrightnessAuto, CATEGORY_DISPLAY, "adaptive automatic light"),
        NexaFlowIconEntry("brightness_high", Icons.Filled.BrightnessHigh, CATEGORY_DISPLAY, "screen light bright"),
        NexaFlowIconEntry("brightness_low", Icons.Filled.BrightnessLow, CATEGORY_DISPLAY, "screen light dim"),
        NexaFlowIconEntry("brightness_medium", Icons.Filled.BrightnessMedium, CATEGORY_DISPLAY, "screen light"),
        NexaFlowIconEntry("contrast", Icons.Filled.Contrast, CATEGORY_DISPLAY, "adjust color invert"),
        NexaFlowIconEntry("screen_rotation", Icons.Filled.ScreenRotation, CATEGORY_DISPLAY, "rotate orientation landscape"),
        NexaFlowIconEntry("palette", Icons.Filled.Palette, CATEGORY_DISPLAY, "color theme art"),
        NexaFlowIconEntry("color_lens", Icons.Filled.ColorLens, CATEGORY_DISPLAY, "color palette paint"),
        NexaFlowIconEntry("flash", Icons.Filled.FlashOn, CATEGORY_DISPLAY, "flashlight torch light"),
        NexaFlowIconEntry("lightbulb", Icons.Filled.Lightbulb, CATEGORY_DISPLAY, "idea lamp bulb"),
        NexaFlowIconEntry("wallpaper", Icons.Filled.Wallpaper, CATEGORY_DISPLAY, "background cover"),
        NexaFlowIconEntry("timer", Icons.Filled.Timer, CATEGORY_DISPLAY, "stopwatch countdown clock"),
        NexaFlowIconEntry("toggle_on", Icons.Filled.ToggleOn, CATEGORY_DISPLAY, "switch on enable"),
        NexaFlowIconEntry("arrow_up", Icons.Filled.ArrowUpward, CATEGORY_DISPLAY, "up increase"),
        NexaFlowIconEntry("arrow_down", Icons.Filled.ArrowDownward, CATEGORY_DISPLAY, "down decrease"),

        // ── Media ──────────────────────────────────────────────────
        NexaFlowIconEntry("play", Icons.Filled.PlayArrow, CATEGORY_MEDIA, "start play media"),
        NexaFlowIconEntry("pause", Icons.Filled.Pause, CATEGORY_MEDIA, "stop hold media"),
        NexaFlowIconEntry("photo", Icons.Filled.Photo, CATEGORY_MEDIA, "image picture camera"),
        NexaFlowIconEntry("camera", Icons.Filled.CameraAlt, CATEGORY_MEDIA, "photo lens shoot"),
        NexaFlowIconEntry("image", Icons.Filled.Image, CATEGORY_MEDIA, "photo picture gallery"),
        NexaFlowIconEntry("movie", Icons.Filled.Tv, CATEGORY_MEDIA, "television video screen"),
        NexaFlowIconEntry("tv", Icons.Filled.Tv, CATEGORY_MEDIA, "television video screen"),
        NexaFlowIconEntry("gamepad", Icons.Filled.Gamepad, CATEGORY_MEDIA, "gaming controller console"),
        NexaFlowIconEntry("memory", Icons.Filled.Memory, CATEGORY_MEDIA, "chip cpu device"),

        // ── System ─────────────────────────────────────────────────
        NexaFlowIconEntry("home", Icons.Filled.Home, CATEGORY_SYSTEM, "house launcher start"),
        NexaFlowIconEntry("schedule", Icons.Filled.Schedule, CATEGORY_SYSTEM, "time clock plan"),
        NexaFlowIconEntry("notifications", Icons.Filled.Notifications, CATEGORY_SYSTEM, "bell alerts"),
        NexaFlowIconEntry("notifications_active", Icons.Filled.NotificationsActive, CATEGORY_SYSTEM, "bell alert active"),
        NexaFlowIconEntry("notifications_off", Icons.Filled.NotificationsOff, CATEGORY_SYSTEM, "bell silent disabled"),
        NexaFlowIconEntry("notification_important", Icons.Filled.NotificationImportant, CATEGORY_SYSTEM, "bell priority alert"),
        NexaFlowIconEntry("lock", Icons.Filled.Lock, CATEGORY_SYSTEM, "secure closed screen lock"),
        NexaFlowIconEntry("lock_open", Icons.Filled.LockOpen, CATEGORY_SYSTEM, "unlock open secure"),
        NexaFlowIconEntry("key", Icons.Filled.Key, CATEGORY_SYSTEM, "unlock password access"),
        NexaFlowIconEntry("power", Icons.Filled.Power, CATEGORY_SYSTEM, "off shutdown restart"),
        NexaFlowIconEntry("power_settings", Icons.Filled.PowerSettingsNew, CATEGORY_SYSTEM, "power button device"),
        NexaFlowIconEntry("terminal", Icons.Filled.Terminal, CATEGORY_SYSTEM, "shell console command code"),
        NexaFlowIconEntry("code", Icons.Filled.Code, CATEGORY_SYSTEM, "programming developer command"),
        NexaFlowIconEntry("build", Icons.Filled.Build, CATEGORY_SYSTEM, "tools wrench repair"),
        NexaFlowIconEntry("bug_report", Icons.Filled.BugReport, CATEGORY_SYSTEM, "insect debug issue"),
        NexaFlowIconEntry("auto_awesome", Icons.Filled.AutoAwesome, CATEGORY_SYSTEM, "magic sparkle effect"),
        NexaFlowIconEntry("tune", Icons.Filled.Tune, CATEGORY_SYSTEM, "sliders adjust controls"),
        NexaFlowIconEntry("store", Icons.Filled.Store, CATEGORY_SYSTEM, "shop market mall"),
        NexaFlowIconEntry("storefront", Icons.Filled.Storefront, CATEGORY_SYSTEM, "shop market store"),
        NexaFlowIconEntry("shopping_cart", Icons.Filled.ShoppingCart, CATEGORY_SYSTEM, "buy purchase shop"),
        NexaFlowIconEntry("credit_card", Icons.Filled.CreditCard, CATEGORY_SYSTEM, "payment money card"),
        NexaFlowIconEntry("apps", Icons.Filled.Apps, CATEGORY_SYSTEM, "grid applications launcher"),
        NexaFlowIconEntry("health", Icons.Filled.HealthAndSafety, CATEGORY_SYSTEM, "medical care safe"),
        NexaFlowIconEntry("monitor_heart", Icons.Filled.MonitorHeart, CATEGORY_SYSTEM, "health pulse heart"),
        NexaFlowIconEntry("fingerprint", Icons.Filled.Fingerprint, CATEGORY_SYSTEM, "biometric secure unlock"),
        NexaFlowIconEntry("verified_user", Icons.Filled.VerifiedUser, CATEGORY_SYSTEM, "trusted secure badge"),
        NexaFlowIconEntry("verified", Icons.Filled.Verified, CATEGORY_SYSTEM, "confirmed badge check"),
        NexaFlowIconEntry("shield", Icons.Filled.Shield, CATEGORY_SYSTEM, "protect secure safety"),
        NexaFlowIconEntry("security", Icons.Filled.Security, CATEGORY_SYSTEM, "protect secure safety"),
        NexaFlowIconEntry("accessibility", Icons.Filled.Accessibility, CATEGORY_SYSTEM, "access assist reach"),
        NexaFlowIconEntry("zoom_in", Icons.Filled.ZoomIn, CATEGORY_SYSTEM, "magnify enlarge search"),
        NexaFlowIconEntry("expand_more", Icons.Filled.ExpandMore, CATEGORY_SYSTEM, "chevron open drop"),
        NexaFlowIconEntry("expand_less", Icons.Filled.ExpandLess, CATEGORY_SYSTEM, "chevron close collapse"),
        NexaFlowIconEntry("keyboard_up", Icons.Filled.KeyboardArrowUp, CATEGORY_SYSTEM, "up arrow navigate"),
        NexaFlowIconEntry("keyboard_down", Icons.Filled.KeyboardArrowDown, CATEGORY_SYSTEM, "down arrow navigate"),

        // ── Battery ────────────────────────────────────────────────
        NexaFlowIconEntry("battery", Icons.Filled.BatteryChargingFull, CATEGORY_BATTERY, "charge power energy"),
        NexaFlowIconEntry("battery_full", Icons.Filled.BatteryFull, CATEGORY_BATTERY, "power energy 100"),
        NexaFlowIconEntry("battery_alert", Icons.Filled.BatteryAlert, CATEGORY_BATTERY, "low charge warning"),
        NexaFlowIconEntry("battery_saver", Icons.Filled.BatterySaver, CATEGORY_BATTERY, "power saving low"),
        NexaFlowIconEntry("energy", Icons.Filled.EnergySavingsLeaf, CATEGORY_BATTERY, "eco power save green"),

        // ── Time ───────────────────────────────────────────────────
        NexaFlowIconEntry("alarm", Icons.Filled.Alarm, CATEGORY_TIME, "wake clock ring"),
        NexaFlowIconEntry("alarm_add", Icons.Filled.AlarmAdd, CATEGORY_TIME, "wake clock add"),
        NexaFlowIconEntry("alarm_on", Icons.Filled.AlarmOn, CATEGORY_TIME, "wake clock active"),
        NexaFlowIconEntry("history", Icons.Filled.History, CATEGORY_TIME, "past log record"),
        NexaFlowIconEntry("event", Icons.Filled.Event, CATEGORY_TIME, "calendar appointment date"),
        NexaFlowIconEntry("date_range", Icons.Filled.DateRange, CATEGORY_TIME, "calendar schedule"),
        NexaFlowIconEntry("query_builder", Icons.Filled.QueryBuilder, CATEGORY_TIME, "clock time schedule"),
        NexaFlowIconEntry("watch_later", Icons.Filled.WatchLater, CATEGORY_TIME, "clock remind later"),
        NexaFlowIconEntry("hourglass", Icons.Filled.HourglassEmpty, CATEGORY_TIME, "wait loading timer"),

        // ── Location ───────────────────────────────────────────────
        NexaFlowIconEntry("location", Icons.Filled.LocationOn, CATEGORY_LOCATION, "gps pin place"),
        NexaFlowIconEntry("place", Icons.Filled.Place, CATEGORY_LOCATION, "pin marker location"),
        NexaFlowIconEntry("map", Icons.Filled.Map, CATEGORY_LOCATION, "navigation route directions"),
        NexaFlowIconEntry("gps", Icons.Filled.GpsFixed, CATEGORY_LOCATION, "location tracking position"),
        NexaFlowIconEntry("my_location", Icons.Filled.MyLocation, CATEGORY_LOCATION, "gps position crosshair")
    )

    /** Backward-compatible flat name→icon list (persisted icon names unchanged). */
    val all: List<Pair<String, ImageVector>> = entries.map { it.name to it.icon }

    /** Ordered category list for the picker's filter chips. */
    val categories: List<String> = listOf(
        CATEGORY_GENERAL,
        CATEGORY_CONNECTIVITY,
        CATEGORY_SOUND,
        CATEGORY_DISPLAY,
        CATEGORY_MEDIA,
        CATEGORY_SYSTEM,
        CATEGORY_BATTERY,
        CATEGORY_TIME,
        CATEGORY_LOCATION
    )

    /**
     * Filters entries by an optional category and a free-text query matched
     * against the name and keywords (case-insensitive).
     */
    fun search(query: String, category: String?): List<NexaFlowIconEntry> {
        val q = query.trim().lowercase()
        return entries.filter { entry ->
            (category == null || entry.category == category) &&
                (q.isEmpty() ||
                    entry.name.contains(q) ||
                    entry.keywords.contains(q))
        }
    }
}

fun iconVector(name: String): ImageVector {
    return NexaFlowIcons.all.find { it.first == name }?.second ?: Icons.Filled.Bolt
}
