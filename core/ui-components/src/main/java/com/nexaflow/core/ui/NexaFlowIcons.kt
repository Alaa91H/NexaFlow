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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FmdGood
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalHotel
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Panorama
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SwitchVideo
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.TransferWithinAStation
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbCloudy
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
        NexaFlowIconEntry("eco", Icons.Filled.Eco, CATEGORY_GENERAL, "nature green leaf"),
        NexaFlowIconEntry("forest", Icons.Filled.Forest, CATEGORY_GENERAL, "nature trees park"),
        NexaFlowIconEntry("park", Icons.Filled.Park, CATEGORY_GENERAL, "nature garden outdoor"),
        NexaFlowIconEntry("water_drop", Icons.Filled.WaterDrop, CATEGORY_GENERAL, "liquid drop rain"),
        NexaFlowIconEntry("air", Icons.Filled.Air, CATEGORY_GENERAL, "wind breeze fresh"),
        NexaFlowIconEntry("ac_unit", Icons.Filled.AcUnit, CATEGORY_GENERAL, "snowflake cold cooling"),
        NexaFlowIconEntry("cloudy", Icons.Filled.WbCloudy, CATEGORY_GENERAL, "weather cloud overcast"),
        NexaFlowIconEntry("nights_stay", Icons.Filled.NightsStay, CATEGORY_GENERAL, "night sleep stars"),
        NexaFlowIconEntry("umbrella", Icons.Filled.Umbrella, CATEGORY_GENERAL, "rain weather protection"),
        NexaFlowIconEntry("landscape", Icons.Filled.Landscape, CATEGORY_GENERAL, "mountains nature scenery"),
        NexaFlowIconEntry("terrain", Icons.Filled.Terrain, CATEGORY_GENERAL, "mountains hills nature"),
        NexaFlowIconEntry("waves", Icons.Filled.Waves, CATEGORY_GENERAL, "ocean sea water"),
        NexaFlowIconEntry("grass", Icons.Filled.Grass, CATEGORY_GENERAL, "lawn nature field"),
        NexaFlowIconEntry("fitness", Icons.Filled.FitnessCenter, CATEGORY_GENERAL, "gym workout exercise"),
        NexaFlowIconEntry("basketball", Icons.Filled.SportsBasketball, CATEGORY_GENERAL, "sport ball game"),
        NexaFlowIconEntry("soccer", Icons.Filled.SportsSoccer, CATEGORY_GENERAL, "football sport game"),
        NexaFlowIconEntry("tennis", Icons.Filled.SportsTennis, CATEGORY_GENERAL, "sport racquet game"),
        NexaFlowIconEntry("run", Icons.Filled.DirectionsRun, CATEGORY_GENERAL, "running exercise sport"),
        NexaFlowIconEntry("trophy", Icons.Filled.EmojiEvents, CATEGORY_GENERAL, "award winner cup"),
        NexaFlowIconEntry("child_care", Icons.Filled.ChildCare, CATEGORY_GENERAL, "baby kid face"),
        NexaFlowIconEntry("child_friendly", Icons.Filled.ChildFriendly, CATEGORY_GENERAL, "baby stroller kids"),
        NexaFlowIconEntry("family", Icons.Filled.FamilyRestroom, CATEGORY_GENERAL, "people family restroom"),
        NexaFlowIconEntry("medication", Icons.Filled.Medication, CATEGORY_GENERAL, "medicine pill health"),
        NexaFlowIconEntry("chat", Icons.Filled.Chat, CATEGORY_GENERAL, "bubble conversation talk"),
        NexaFlowIconEntry("forum", Icons.Filled.Forum, CATEGORY_GENERAL, "discussion community talk"),
        NexaFlowIconEntry("voicemail", Icons.Filled.Voicemail, CATEGORY_GENERAL, "message audio record"),
        NexaFlowIconEntry("inbox", Icons.Filled.Inbox, CATEGORY_GENERAL, "mail tray receive"),
        NexaFlowIconEntry("call_made", Icons.Filled.CallMade, CATEGORY_GENERAL, "phone arrow outgoing"),
        NexaFlowIconEntry("call_received", Icons.Filled.CallReceived, CATEGORY_GENERAL, "phone arrow incoming"),

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
        NexaFlowIconEntry("router", Icons.Filled.Router, CATEGORY_CONNECTIVITY, "network device wifi"),
        NexaFlowIconEntry("lan", Icons.Filled.Lan, CATEGORY_CONNECTIVITY, "network ethernet cable"),
        NexaFlowIconEntry("storage", Icons.Filled.Storage, CATEGORY_CONNECTIVITY, "disk drive data"),
        NexaFlowIconEntry("print", Icons.Filled.Print, CATEGORY_CONNECTIVITY, "printer document paper"),
        NexaFlowIconEntry("scanner", Icons.Filled.Scanner, CATEGORY_CONNECTIVITY, "scan document device"),
        NexaFlowIconEntry("desktop", Icons.Filled.DesktopWindows, CATEGORY_CONNECTIVITY, "computer monitor pc"),
        NexaFlowIconEntry("laptop", Icons.Filled.Laptop, CATEGORY_CONNECTIVITY, "computer notebook"),
        NexaFlowIconEntry("tablet", Icons.Filled.TabletMac, CATEGORY_CONNECTIVITY, "ipad device screen"),
        NexaFlowIconEntry("watch", Icons.Filled.Watch, CATEGORY_CONNECTIVITY, "wearable smartwatch time"),
        NexaFlowIconEntry("headset_mic", Icons.Filled.HeadsetMic, CATEGORY_CONNECTIVITY, "headset microphone call"),
        NexaFlowIconEntry("hearing", Icons.Filled.Hearing, CATEGORY_CONNECTIVITY, "listen ear hearing aid"),
        NexaFlowIconEntry("dns", Icons.Filled.Dns, CATEGORY_CONNECTIVITY, "server network domain"),
        NexaFlowIconEntry("commute", Icons.Filled.Commute, CATEGORY_CONNECTIVITY, "transport trip travel"),
        NexaFlowIconEntry("subway", Icons.Filled.Subway, CATEGORY_CONNECTIVITY, "metro train underground"),
        NexaFlowIconEntry("train", Icons.Filled.Train, CATEGORY_CONNECTIVITY, "railway travel"),
        NexaFlowIconEntry("tram", Icons.Filled.Tram, CATEGORY_CONNECTIVITY, "streetcar transit"),
        NexaFlowIconEntry("bus", Icons.Filled.DirectionsBus, CATEGORY_CONNECTIVITY, "transport vehicle route"),
        NexaFlowIconEntry("car", Icons.Filled.DirectionsCar, CATEGORY_CONNECTIVITY, "vehicle automobile drive"),
        NexaFlowIconEntry("boat", Icons.Filled.DirectionsBoat, CATEGORY_CONNECTIVITY, "ship ferry water"),
        NexaFlowIconEntry("walk", Icons.Filled.DirectionsWalk, CATEGORY_CONNECTIVITY, "walking pedestrian route"),
        NexaFlowIconEntry("departure", Icons.Filled.DepartureBoard, CATEGORY_CONNECTIVITY, "schedule transit depart"),
        NexaFlowIconEntry("taxi", Icons.Filled.LocalTaxi, CATEGORY_CONNECTIVITY, "cab ride transport"),
        NexaFlowIconEntry("airport_shuttle", Icons.Filled.AirportShuttle, CATEGORY_CONNECTIVITY, "van airport transfer"),
        NexaFlowIconEntry("motorcycle", Icons.Filled.Motorcycle, CATEGORY_CONNECTIVITY, "bike motorbike ride"),

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
        NexaFlowIconEntry("headset", Icons.Filled.Headset, CATEGORY_SOUND, "headphones listen audio"),
        NexaFlowIconEntry("podcasts", Icons.Filled.Podcasts, CATEGORY_SOUND, "radio show listen"),
        NexaFlowIconEntry("playlist_play", Icons.Filled.PlaylistPlay, CATEGORY_SOUND, "list queue play"),
        NexaFlowIconEntry("playlist_add", Icons.Filled.PlaylistAdd, CATEGORY_SOUND, "list queue add"),
        NexaFlowIconEntry("replay", Icons.Filled.Replay, CATEGORY_SOUND, "repeat rewind again"),
        NexaFlowIconEntry("fast_forward", Icons.Filled.FastForward, CATEGORY_SOUND, "speed skip ahead"),
        NexaFlowIconEntry("fast_rewind", Icons.Filled.FastRewind, CATEGORY_SOUND, "rewind back skip"),
        NexaFlowIconEntry("skip_next", Icons.Filled.SkipNext, CATEGORY_SOUND, "next track media"),
        NexaFlowIconEntry("skip_previous", Icons.Filled.SkipPrevious, CATEGORY_SOUND, "previous track media"),
        NexaFlowIconEntry("shuffle", Icons.Filled.Shuffle, CATEGORY_SOUND, "random mix order"),
        NexaFlowIconEntry("repeat", Icons.Filled.Repeat, CATEGORY_SOUND, "loop again cycle"),
        NexaFlowIconEntry("stop", Icons.Filled.Stop, CATEGORY_SOUND, "square halt media"),
        NexaFlowIconEntry("videocam", Icons.Filled.Videocam, CATEGORY_SOUND, "video camera record"),
        NexaFlowIconEntry("video_call", Icons.Filled.VideoCall, CATEGORY_SOUND, "video camera call"),
        NexaFlowIconEntry("live_tv", Icons.Filled.LiveTv, CATEGORY_SOUND, "television broadcast live"),
        NexaFlowIconEntry("surround_sound", Icons.Filled.SurroundSound, CATEGORY_SOUND, "audio speaker channels"),

        // ── Display ────────────────────────────────────────────────
        NexaFlowIconEntry("sunny", Icons.Filled.WbSunny, CATEGORY_DISPLAY, "light brightness day"),
        NexaFlowIconEntry("dark", Icons.Filled.DarkMode, CATEGORY_DISPLAY, "night moon dark mode"),
        NexaFlowIconEntry("light", Icons.Filled.LightMode, CATEGORY_DISPLAY, "sun day bright light mode"),
        NexaFlowIconEntry("nightlight", Icons.Filled.Nightlight, CATEGORY_DISPLAY, "night moon sleep"),
        NexaFlowIconEntry("brightness", Icons.Filled.BrightnessMedium, CATEGORY_DISPLAY, "screen light level"),
        NexaFlowIconEntry("brightness_auto", Icons.Filled.BrightnessAuto, CATEGORY_DISPLAY, "adaptive automatic light"),
        NexaFlowIconEntry("brightness_high", Icons.Filled.BrightnessHigh, CATEGORY_DISPLAY, "screen light bright"),
        NexaFlowIconEntry("brightness_low", Icons.Filled.BrightnessLow, CATEGORY_DISPLAY, "screen light dim"),
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
        NexaFlowIconEntry("fullscreen", Icons.Filled.Fullscreen, CATEGORY_DISPLAY, "expand maximize screen"),
        NexaFlowIconEntry("fullscreen_exit", Icons.Filled.FullscreenExit, CATEGORY_DISPLAY, "shrink minimize screen"),
        NexaFlowIconEntry("picture_in_picture", Icons.Filled.PictureInPicture, CATEGORY_DISPLAY, "pip overlay screen"),
        NexaFlowIconEntry("aspect_ratio", Icons.Filled.AspectRatio, CATEGORY_DISPLAY, "screen ratio proportion"),
        NexaFlowIconEntry("blur", Icons.Filled.BlurOn, CATEGORY_DISPLAY, "blur effect soften"),
        NexaFlowIconEntry("exposure", Icons.Filled.Exposure, CATEGORY_DISPLAY, "photo light adjust"),
        NexaFlowIconEntry("gradient", Icons.Filled.Gradient, CATEGORY_DISPLAY, "color fade blend"),

        // ── Media ──────────────────────────────────────────────────
        NexaFlowIconEntry("play", Icons.Filled.PlayArrow, CATEGORY_MEDIA, "start play media"),
        NexaFlowIconEntry("pause", Icons.Filled.Pause, CATEGORY_MEDIA, "stop hold media"),
        NexaFlowIconEntry("photo", Icons.Filled.Photo, CATEGORY_MEDIA, "image picture camera"),
        NexaFlowIconEntry("camera", Icons.Filled.CameraAlt, CATEGORY_MEDIA, "photo lens shoot"),
        NexaFlowIconEntry("image", Icons.Filled.Image, CATEGORY_MEDIA, "photo picture gallery"),
        NexaFlowIconEntry("movie", Icons.Filled.Movie, CATEGORY_MEDIA, "film cinema movie"),
        NexaFlowIconEntry("tv", Icons.Filled.Tv, CATEGORY_MEDIA, "television video screen"),
        NexaFlowIconEntry("gamepad", Icons.Filled.Gamepad, CATEGORY_MEDIA, "gaming controller console"),
        NexaFlowIconEntry("memory", Icons.Filled.Memory, CATEGORY_MEDIA, "chip cpu device"),
        NexaFlowIconEntry("video_library", Icons.Filled.VideoLibrary, CATEGORY_MEDIA, "videos collection library"),
        NexaFlowIconEntry("collections", Icons.Filled.Collections, CATEGORY_MEDIA, "photos gallery album"),
        NexaFlowIconEntry("photo_library", Icons.Filled.PhotoLibrary, CATEGORY_MEDIA, "pictures gallery album"),
        NexaFlowIconEntry("add_photo", Icons.Filled.AddPhotoAlternate, CATEGORY_MEDIA, "camera add picture"),
        NexaFlowIconEntry("panorama", Icons.Filled.Panorama, CATEGORY_MEDIA, "wide photo landscape"),
        NexaFlowIconEntry("filter", Icons.Filled.Filter, CATEGORY_MEDIA, "photo effect edit"),
        NexaFlowIconEntry("camera_rear", Icons.Filled.CameraRear, CATEGORY_MEDIA, "camera back photo"),
        NexaFlowIconEntry("camera_front", Icons.Filled.CameraFront, CATEGORY_MEDIA, "camera selfie photo"),
        NexaFlowIconEntry("slideshow", Icons.Filled.Slideshow, CATEGORY_MEDIA, "play presentation slides"),
        NexaFlowIconEntry("ondemand_video", Icons.Filled.OndemandVideo, CATEGORY_MEDIA, "video streaming play"),
        NexaFlowIconEntry("video_file", Icons.Filled.VideoFile, CATEGORY_MEDIA, "video clip file"),
        NexaFlowIconEntry("theaters", Icons.Filled.Theaters, CATEGORY_MEDIA, "cinema movies theater"),
        NexaFlowIconEntry("switch_video", Icons.Filled.SwitchVideo, CATEGORY_MEDIA, "switch camera video"),

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
        NexaFlowIconEntry("engineering", Icons.Filled.Engineering, CATEGORY_SYSTEM, "engineer worker hardhat"),
        NexaFlowIconEntry("architecture", Icons.Filled.Architecture, CATEGORY_SYSTEM, "building design plan"),
        NexaFlowIconEntry("construction", Icons.Filled.Construction, CATEGORY_SYSTEM, "building crane work"),
        NexaFlowIconEntry("calculate", Icons.Filled.Calculate, CATEGORY_SYSTEM, "calculator math numbers"),
        NexaFlowIconEntry("notes", Icons.Filled.Notes, CATEGORY_SYSTEM, "document writing memo"),
        NexaFlowIconEntry("description", Icons.Filled.Description, CATEGORY_SYSTEM, "document file text"),
        NexaFlowIconEntry("article", Icons.Filled.Article, CATEGORY_SYSTEM, "document page write"),
        NexaFlowIconEntry("menu_book", Icons.Filled.MenuBook, CATEGORY_SYSTEM, "book reading guide"),
        NexaFlowIconEntry("library_books", Icons.Filled.LibraryBooks, CATEGORY_SYSTEM, "books library reading"),
        NexaFlowIconEntry("book", Icons.Filled.Book, CATEGORY_SYSTEM, "reading novel textbook"),
        NexaFlowIconEntry("event_note", Icons.Filled.EventNote, CATEGORY_SYSTEM, "calendar note plan"),
        NexaFlowIconEntry("checklist", Icons.Filled.Checklist, CATEGORY_SYSTEM, "list tasks done"),
        NexaFlowIconEntry("task_alt", Icons.Filled.TaskAlt, CATEGORY_SYSTEM, "task done complete"),
        NexaFlowIconEntry("account_tree", Icons.Filled.AccountTree, CATEGORY_SYSTEM, "tree hierarchy flow"),
        NexaFlowIconEntry("category", Icons.Filled.Category, CATEGORY_SYSTEM, "folders group classify"),
        NexaFlowIconEntry("list", Icons.Filled.List, CATEGORY_SYSTEM, "list lines menu"),
        NexaFlowIconEntry("view_list", Icons.Filled.ViewList, CATEGORY_SYSTEM, "list view rows"),
        NexaFlowIconEntry("copy", Icons.Filled.ContentCopy, CATEGORY_SYSTEM, "duplicate copy files"),
        NexaFlowIconEntry("cut", Icons.Filled.ContentCut, CATEGORY_SYSTEM, "scissors clip cut"),
        NexaFlowIconEntry("launch", Icons.Filled.Launch, CATEGORY_SYSTEM, "open external new tab"),
        NexaFlowIconEntry("open_in_new", Icons.Filled.OpenInNew, CATEGORY_SYSTEM, "open external window"),
        NexaFlowIconEntry("open_in_full", Icons.Filled.OpenInFull, CATEGORY_SYSTEM, "expand fullscreen open"),
        NexaFlowIconEntry("sort", Icons.Filled.Sort, CATEGORY_SYSTEM, "order arrange sort"),
        NexaFlowIconEntry("filter_list", Icons.Filled.FilterList, CATEGORY_SYSTEM, "filter funnel list"),
        NexaFlowIconEntry("label", Icons.Filled.Label, CATEGORY_SYSTEM, "tag category label"),
        NexaFlowIconEntry("sell", Icons.Filled.Sell, CATEGORY_SYSTEM, "price tag sale"),
        NexaFlowIconEntry("tag", Icons.Filled.Tag, CATEGORY_SYSTEM, "label tag marker"),
        NexaFlowIconEntry("local_offer", Icons.Filled.LocalOffer, CATEGORY_SYSTEM, "offer discount tag"),
        NexaFlowIconEntry("redeem", Icons.Filled.Redeem, CATEGORY_SYSTEM, "gift present reward"),
        NexaFlowIconEntry("gift_card", Icons.Filled.CardGiftcard, CATEGORY_SYSTEM, "gift card present"),
        NexaFlowIconEntry("ticket", Icons.Filled.ConfirmationNumber, CATEGORY_SYSTEM, "ticket number event"),
        NexaFlowIconEntry("payments", Icons.Filled.Payments, CATEGORY_SYSTEM, "payment money cash"),
        NexaFlowIconEntry("receipt", Icons.Filled.Receipt, CATEGORY_SYSTEM, "receipt bill invoice"),
        NexaFlowIconEntry("receipt_long", Icons.Filled.ReceiptLong, CATEGORY_SYSTEM, "receipt invoice list"),
        NexaFlowIconEntry("account_balance", Icons.Filled.AccountBalance, CATEGORY_SYSTEM, "bank building money"),
        NexaFlowIconEntry("wallet", Icons.Filled.AccountBalanceWallet, CATEGORY_SYSTEM, "wallet card money"),
        NexaFlowIconEntry("money", Icons.Filled.AttachMoney, CATEGORY_SYSTEM, "cash dollar currency"),
        NexaFlowIconEntry("currency_exchange", Icons.Filled.CurrencyExchange, CATEGORY_SYSTEM, "money convert swap"),
        NexaFlowIconEntry("trending_up", Icons.Filled.TrendingUp, CATEGORY_SYSTEM, "chart grow increase"),
        NexaFlowIconEntry("trending_down", Icons.Filled.TrendingDown, CATEGORY_SYSTEM, "chart drop decrease"),
        NexaFlowIconEntry("bar_chart", Icons.Filled.BarChart, CATEGORY_SYSTEM, "chart bars analytics"),
        NexaFlowIconEntry("pie_chart", Icons.Filled.PieChart, CATEGORY_SYSTEM, "chart pie analytics"),
        NexaFlowIconEntry("donut", Icons.Filled.DonutLarge, CATEGORY_SYSTEM, "chart donut analytics"),
        NexaFlowIconEntry("savings", Icons.Filled.Savings, CATEGORY_SYSTEM, "piggy bank save money"),
        NexaFlowIconEntry("shopping_bag", Icons.Filled.ShoppingBag, CATEGORY_SYSTEM, "shopping bag store"),
        NexaFlowIconEntry("shopping_basket", Icons.Filled.ShoppingBasket, CATEGORY_SYSTEM, "basket shopping market"),
        NexaFlowIconEntry("local_mall", Icons.Filled.LocalMall, CATEGORY_SYSTEM, "mall shopping store"),

        // ── Battery ────────────────────────────────────────────────
        NexaFlowIconEntry("battery", Icons.Filled.BatteryChargingFull, CATEGORY_BATTERY, "charge power energy"),
        NexaFlowIconEntry("battery_std", Icons.Filled.BatteryStd, CATEGORY_BATTERY, "power battery level"),
        NexaFlowIconEntry("battery_unknown", Icons.Filled.BatteryUnknown, CATEGORY_BATTERY, "power level unknown"),
        NexaFlowIconEntry("battery_0", Icons.Filled.Battery0Bar, CATEGORY_BATTERY, "power empty level"),
        NexaFlowIconEntry("battery_1", Icons.Filled.Battery1Bar, CATEGORY_BATTERY, "power low level"),
        NexaFlowIconEntry("offline_bolt", Icons.Filled.OfflineBolt, CATEGORY_BATTERY, "quick charge fast power"),
        NexaFlowIconEntry("electric_bolt", Icons.Filled.ElectricBolt, CATEGORY_BATTERY, "electric zap energy"),
        NexaFlowIconEntry("solar", Icons.Filled.SolarPower, CATEGORY_BATTERY, "solar sun power energy"),
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
        NexaFlowIconEntry("calendar_month", Icons.Filled.CalendarMonth, CATEGORY_TIME, "calendar month date"),
        NexaFlowIconEntry("calendar_today", Icons.Filled.CalendarToday, CATEGORY_TIME, "calendar today date"),
        NexaFlowIconEntry("update", Icons.Filled.Update, CATEGORY_TIME, "update refresh sync"),
        NexaFlowIconEntry("more_time", Icons.Filled.MoreTime, CATEGORY_TIME, "time add more clock"),
        NexaFlowIconEntry("pending", Icons.Filled.Pending, CATEGORY_TIME, "waiting pending clock"),
        NexaFlowIconEntry("hourglass_top", Icons.Filled.HourglassTop, CATEGORY_TIME, "timer top loading"),
        NexaFlowIconEntry("hourglass_bottom", Icons.Filled.HourglassBottom, CATEGORY_TIME, "timer bottom loading"),

        // ── Location ───────────────────────────────────────────────
        NexaFlowIconEntry("location", Icons.Filled.LocationOn, CATEGORY_LOCATION, "gps pin place"),
        NexaFlowIconEntry("place", Icons.Filled.Place, CATEGORY_LOCATION, "pin marker location"),
        NexaFlowIconEntry("map", Icons.Filled.Map, CATEGORY_LOCATION, "navigation route directions"),
        NexaFlowIconEntry("gps", Icons.Filled.GpsFixed, CATEGORY_LOCATION, "location tracking position"),
        NexaFlowIconEntry("my_location", Icons.Filled.MyLocation, CATEGORY_LOCATION, "gps position crosshair"),
        NexaFlowIconEntry("directions", Icons.Filled.Directions, CATEGORY_LOCATION, "route navigation sign"),
        NexaFlowIconEntry("navigation", Icons.Filled.Navigation, CATEGORY_LOCATION, "arrow route direction"),
        NexaFlowIconEntry("explore", Icons.Filled.Explore, CATEGORY_LOCATION, "compass explore discover"),
        NexaFlowIconEntry("near_me", Icons.Filled.NearMe, CATEGORY_LOCATION, "arrow nearby direction"),
        NexaFlowIconEntry("add_location", Icons.Filled.AddLocation, CATEGORY_LOCATION, "pin add new location"),
        NexaFlowIconEntry("pin_drop", Icons.Filled.PinDrop, CATEGORY_LOCATION, "pin drop marker"),
        NexaFlowIconEntry("fmd_good", Icons.Filled.FmdGood, CATEGORY_LOCATION, "pin location place"),
        NexaFlowIconEntry("location_city", Icons.Filled.LocationCity, CATEGORY_LOCATION, "city building location"),
        NexaFlowIconEntry("parking", Icons.Filled.LocalParking, CATEGORY_LOCATION, "parking p sign"),
        NexaFlowIconEntry("gas_station", Icons.Filled.LocalGasStation, CATEGORY_LOCATION, "fuel pump station"),
        NexaFlowIconEntry("hotel", Icons.Filled.LocalHotel, CATEGORY_LOCATION, "hotel bed stay"),
        NexaFlowIconEntry("library", Icons.Filled.LocalLibrary, CATEGORY_LOCATION, "library books study"),
        NexaFlowIconEntry("hospital", Icons.Filled.LocalHospital, CATEGORY_LOCATION, "hospital medical cross"),
        NexaFlowIconEntry("cafe", Icons.Filled.LocalCafe, CATEGORY_LOCATION, "coffee cafe drink"),
        NexaFlowIconEntry("bar", Icons.Filled.LocalBar, CATEGORY_LOCATION, "bar drink cocktail"),
        NexaFlowIconEntry("restaurant", Icons.Filled.Restaurant, CATEGORY_LOCATION, "food dining fork"),
        NexaFlowIconEntry("restaurant_menu", Icons.Filled.RestaurantMenu, CATEGORY_LOCATION, "menu food dining"),
        NexaFlowIconEntry("fastfood", Icons.Filled.Fastfood, CATEGORY_LOCATION, "burger fast food"),
        NexaFlowIconEntry("cake", Icons.Filled.Cake, CATEGORY_LOCATION, "cake birthday dessert"),
        NexaFlowIconEntry("icecream", Icons.Filled.Icecream, CATEGORY_LOCATION, "ice cream dessert"),
        NexaFlowIconEntry("pizza", Icons.Filled.LocalPizza, CATEGORY_LOCATION, "pizza food"),
        NexaFlowIconEntry("shipping", Icons.Filled.LocalShipping, CATEGORY_LOCATION, "delivery truck shipping"),
        NexaFlowIconEntry("grocery", Icons.Filled.LocalGroceryStore, CATEGORY_LOCATION, "grocery store cart"),
        NexaFlowIconEntry("florist", Icons.Filled.LocalFlorist, CATEGORY_LOCATION, "flowers florist shop"),
        NexaFlowIconEntry("station", Icons.Filled.TransferWithinAStation, CATEGORY_LOCATION, "station transfer transit")
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
        CATEGORY_APPS,
        CATEGORY_SECURITY,
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
    // Legacy names removed from the catalog in the dedupe pass: keep them
    // resolving so already-saved tasks never fall back to the default bolt.
    val legacy = mapOf(
        "brightness_medium" to Icons.Filled.BrightnessMedium
    )
    return NexaFlowIcons.all.find { it.first == name }?.second
        ?: legacy[name]
        ?: Icons.Filled.Bolt
}
