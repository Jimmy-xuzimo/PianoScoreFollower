"use strict";
const translations = {
  zh: {
    skip: "跳转到内容",
    navigation: "主导航",
    mobileNavigation: "移动导航",
    openMenu: "打开导航",
    closeMenu: "关闭导航",
    navFeatures: "功能",
    navExperience: "界面体验",
    navStart: "开始使用",
    heroTag: "给每一次专注的练习",
    hero1: "让乐谱，",
    hero2: "跟上你的演奏。",
    heroDesc:
      "双手留给琴键，目光留给旋律。导入你的乐谱，让实时跟谱与自动翻页，陪你完成下一次练习。",
    download: "下载 Android 版",
    tryWeb: "体验网页版",
    heroNote: "开源 · Android 10+ · 独立网页版",
    demoTitle: "跟谱交互示意",
    scoreAlt: "应用内 Minuet in C 乐谱截图",
    sampleNote: "示意音符",
    reset: "重置示意",
    playDemo: "播放示意",
    pauseDemo: "暂停示意",
    demoNote: "基于真实乐谱截图的视觉示意，无音频、无需麦克风。",
    formatsLabel: "从你的乐谱开始",
    photos: "照片",
    formatsEnd: "不同格式，各有合适的练习方式。",
    featuresTag: "少些分心，多些音乐",
    featuresTitle: "让练习，回到演奏本身。",
    featuresDesc: "从第一份乐谱到下一段旋律，给日常练习一个顺手的起点。",
    followTitle: "你弹到哪，谱跟到哪。",
    followDesc:
      "通过麦克风识别音高，与结构化乐谱匹配，驱动光标前进。逐行跟随与自动翻页，让目光更从容。",
    followFoot: "实时识别 · 逐行跟随 · 自动翻页",
    scrollTitle: "纸上的谱，也能流动。",
    scrollDesc:
      "导入 PDF 或照片，按顺序排好页面、设定总时长，即可匀速滚动。随时暂停，也能手动调整速度。",
    scrollFoot: "PDF / 照片使用定时滚动，无需音高匹配",
    tuneTitle: "从一个准音开始。",
    tuneDesc:
      "弹奏中央 C 完成基准校准，再查看当前音符与音分偏差。多次采样取中位数，降低单次误判的影响。",
    tuneFoot: "中央 C 校准 · 音符识别 · 音分显示",
    experienceTag: "走近真实界面",
    experienceTitle: "每一页，都为练习而设计。",
    experienceDesc:
      "以下为仓库中的 Android 应用截图。\n切换场景，了解不同的练习方式。",
    screenTabs: "应用界面",
    screenNotation: "乐谱阅读",
    screenNotationSub: "导入、排版，开始下一首。",
    screenPlaying: "播放与跟谱",
    screenPlayingSub: "光标前进，练习不断。",
    screenTuner: "音准校准",
    screenTunerSub: "留意音高，也听见细节。",
    screenSettings: "个性外观",
    screenSettingsSub: "深浅主题，五种预设色调。",
    notationDesc:
      "MIDI 与 MusicXML 交由 alphaTab 排版，支持播放与点按定位。导入的乐谱保存于本地，重启后仍可继续。",
    playingDesc:
      "播放、暂停或停止乐谱，也可以点按谱面定位。使用麦克风跟谱时，根据识别结果推进光标与页面。",
    tunerDesc:
      "先弹中央 C（C4）建立基准，再查看当前音符与音分偏差。建议在真实设备和合适的环境音量下使用。",
    settingsDesc:
      "跟随系统或手动选择深浅模式，使用壁纸取色或五种预设主题。偏好保存于本地，旋转屏幕时保持光标位置。",
    notationAlt: "Android 应用的乐谱阅读界面",
    playingAlt: "Android 应用的乐谱播放界面",
    tunerAlt: "Android 应用的音准校准界面",
    settingsAlt: "Android 应用的外观设置界面",
    openWeb: "打开独立网页版",
    screenshotNote: "真实应用截图 · 界面语言以应用为准",
    stepsTag: "简单开始，慢慢进步",
    stepsTitle: "三步，进入你的练习时间。",
    step1Title: "带上你的乐谱",
    step1Desc:
      "导入 MIDI / MusicXML，或用 PDF 与照片建立滚动谱。也可以从应用内置示例开始。",
    step2Title: "选一种练习方式",
    step2Desc:
      "结构化乐谱可开启麦克风跟谱；PDF 与照片设定滚动时长。需要时，先用中央 C 校准。",
    step3Title: "把注意力交给音乐",
    step3Desc:
      "跟随光标、播放乐谱，或让页面按节奏滚动。随时暂停、调整，再继续练习。",
    craftTag: "开放的代码，认真的细节",
    craftTitle: "音乐之外，\n也值得仔细打磨。",
    craftDesc:
      "原生 Android 界面、alphaTab 乐谱引擎与自研 DSP 管线共同工作。从音频采集到跟谱对齐，源码都在这里。",
    exploreSource: "探索 GitHub 源码",
    craftUi: "Material 3 界面，横竖屏适配与本地偏好保存。",
    craftScore: "在 WebView 中完成乐谱排版与声音合成。",
    craftDsp: "音高检测、色度特征与跟谱对齐管线。",
    startTag: "下一首，从这里开始",
    startTitle: "选择你的练习入口。",
    startDesc: "Android 应用与独立网页版，各自发布、各自运行。",
    androidTitle: "在你的 Android 设备上。",
    androidDesc:
      "适合手机与平板，Android 10 及以上。前往 GitHub Releases 查看当前安装包与发行说明。",
    androidFoot: "APK 通过 GitHub Releases 分发",
    webTitle: "也可以，先打开浏览器。",
    webDesc:
      "无需安装 APK，即可体验独立网页版本。在实际跟谱时，根据浏览器提示允许麦克风访问。",
    webFoot: "查看网页版独立仓库 ↗",
    releaseNote:
      "本页依据 Android 当前主分支介绍功能，发行包支持情况请以 Release 说明为准。首次启动可能需要数秒初始化乐谱引擎与音源。",
    faqTag: "练习之前",
    faqTitle: "几个你可能关心的问题。",
    faqIntro: "更多安装说明与已知限制，见项目 README。",
    readDocs: "阅读文档",
    faq1q: "PDF 和照片也能根据声音自动跟谱吗？",
    faq1a:
      "PDF 与照片使用定时滚动模式：按顺序排列页面，设定总时长，再调整滚动速度。麦克风驱动的音符匹配与自动跟谱用于 MIDI / MusicXML 等结构化乐谱；项目未提供 PDF 或照片的乐谱识别转换功能。",
    faq2q: "识别效果会受到什么影响？",
    faq2a:
      "跟谱依赖真实麦克风输入。环境噪声、设备麦克风、音量与演奏内容都会影响识别效果。建议在真机上测试，不以模拟器表现作为判断依据；本项目不承诺所有环境下的识别准确率。",
    faq3q: "为什么首次启动需要等待？",
    faq3a:
      "首次冷启动需要初始化 WebView，并解码采样钢琴音源。部分设备可能需要数秒，模拟器可能更久。加载超时后应用提供重试，后续启动通常更快。",
    faq4q: "这个介绍网站会访问麦克风吗？",
    faq4a:
      "不会。本页的跟谱演示只是基于真实截图的视觉动画，不播放音频，也不采集声音。要体验实际识别，请使用 Android 应用或独立网页版。",
    faq5q: "可以参与开发或二次修改吗？",
    faq5a:
      "项目源码采用 BSD 3-Clause 许可证。欢迎查看源码、提交问题或改进；分发时还需保留 alphaTab、Bravura 字体及音源等第三方资源对应的许可与署名信息。",
    closingTag: "A LITTLE LESS FRICTION. A LITTLE MORE MUSIC.",
    closingTitle: "下一段旋律，留给你。",
    closingDesc: "让工具安静地陪伴，让练习继续发生。",
    closingAction: "开始一次练习",
    footerTag: "一个开源的钢琴练习伴侣。",
    feedback: "反馈建议",
    privacy: "隐私政策",
    credits: "乐谱排版由 alphaTab 提供",
    themePrefix: "主题：",
    themeSystem: "跟随系统",
    themeLight: "浅色",
    themeDark: "深色",
    themeNext: "切换至",
    title: "智能琴谱 · PianoScoreFollower — 让乐谱，跟上你的演奏。",
    description:
      "PianoScoreFollower 智能琴谱：为 Android 打造的开源乐谱伴侣。实时跟谱、自动翻页、PDF 与照片滚动谱、音准校准，也可体验独立网页版。",
  },
  en: {
    skip: "Skip to content",
    navigation: "Main navigation",
    mobileNavigation: "Mobile navigation",
    openMenu: "Open navigation",
    closeMenu: "Close navigation",
    navFeatures: "Features",
    navExperience: "Experience",
    navStart: "Get started",
    heroTag: "A COMPANION FOR FOCUSED PRACTICE",
    hero1: "Stay in the music.",
    hero2: "Let the score follow.",
    heroDesc:
      "Keep your hands on the keys and your attention on the melody. Bring your scores, follow along, and turn the page without breaking your practice.",
    download: "Get Android app",
    tryWeb: "Try the web app",
    heroNote: "Open source · Android 10+ · Separate web app",
    demoTitle: "Score-following illustration",
    scoreAlt: "Minuet in C score screenshot from the Android app",
    sampleNote: "Sample note",
    reset: "Reset illustration",
    playDemo: "Play illustration",
    pauseDemo: "Pause illustration",
    demoNote:
      "A visual illustration using an actual score screenshot. No audio or microphone.",
    formatsLabel: "START WITH YOUR SCORE",
    photos: "Photos",
    formatsEnd: "Different formats. A fitting way to practice.",
    featuresTag: "LESS DISTRACTION. MORE MUSIC.",
    featuresTitle: "Bring practice back to playing.",
    featuresDesc:
      "From the first score to the next phrase, a thoughtful starting point for everyday practice.",
    followTitle: "A score that follows your lead.",
    followDesc:
      "Microphone-based pitch detection matches your playing to structured scores, advancing the cursor. Line following and automatic page turns keep the music in view.",
    followFoot: "Pitch detection · Line following · Auto page turns",
    scrollTitle: "Give paper scores a little flow.",
    scrollDesc:
      "Import PDFs or photos, arrange the pages and set a total duration for steady scrolling. Pause whenever you need, or adjust the speed by hand.",
    scrollFoot: "PDFs and photos use timed scrolling, not pitch matching",
    tuneTitle: "Begin on the right note.",
    tuneDesc:
      "Calibrate with middle C, then see the detected note and its deviation in cents. Median sampling helps reduce the effect of a single mistaken reading.",
    tuneFoot: "Middle C calibration · Note detection · Cents display",
    experienceTag: "A CLOSER LOOK",
    experienceTitle: "A place for every part of practice.",
    experienceDesc:
      "Actual Android screenshots from the repository.\nExplore the different ways to practice.",
    screenTabs: "Application screenshots",
    screenNotation: "Read your scores",
    screenNotationSub: "Import, render, begin the next piece.",
    screenPlaying: "Play & follow",
    screenPlayingSub: "A moving cursor. An unbroken phrase.",
    screenTuner: "Pitch calibration",
    screenTunerSub: "Listen closely to the little details.",
    screenSettings: "Make it yours",
    screenSettingsSub: "Light, dark and five preset palettes.",
    notationDesc:
      "alphaTab renders MIDI and MusicXML with playback and tap-to-seek navigation. Imported scores are stored locally and remain available after restarting the app.",
    playingDesc:
      "Play, pause or stop a score, and tap to seek. With microphone-based following enabled, detected notes guide the cursor and page progression.",
    tunerDesc:
      "Play middle C (C4) to establish a reference, then inspect the detected note and cents deviation. Use a real device with suitable ambient sound levels.",
    settingsDesc:
      "Follow the system or choose light and dark themes. Use wallpaper colors or five preset palettes. Preferences are stored locally, and screen rotation preserves the cursor position.",
    notationAlt: "Android app score-reading screen",
    playingAlt: "Android app score-playback screen",
    tunerAlt: "Android app pitch-calibration screen",
    settingsAlt: "Android app appearance-settings screen",
    openWeb: "Open the separate web app",
    screenshotNote: "Actual app screenshots · In-app text shown as captured",
    stepsTag: "A SIMPLE START",
    stepsTitle: "Three steps into practice.",
    step1Title: "Bring a score",
    step1Desc:
      "Import MIDI or MusicXML, create a scrolling score from PDFs and photos, or start with one of the built-in examples.",
    step2Title: "Find your way to practice",
    step2Desc:
      "Enable microphone following for structured scores, or set a scrolling duration for PDFs and photos. Calibrate with middle C when needed.",
    step3Title: "Give the music your attention",
    step3Desc:
      "Follow the cursor, play back the score, or let pages scroll at a steady pace. Pause, adjust and pick up where you left off.",
    craftTag: "OPEN CODE. THOUGHTFUL DETAILS.",
    craftTitle: "Crafted beyond\nthe notes.",
    craftDesc:
      "A native Android interface, the alphaTab notation engine and a custom DSP pipeline work together. From audio capture to score alignment, the source is here to explore.",
    exploreSource: "Explore the GitHub source",
    craftUi:
      "Material 3, portrait and landscape layouts, and local preferences.",
    craftScore: "Score rendering and sound synthesis inside a WebView.",
    craftDsp: "Pitch detection, chroma features and score alignment.",
    startTag: "YOUR NEXT PIECE STARTS HERE",
    startTitle: "Choose your way to practice.",
    startDesc: "The Android and web apps are released and hosted separately.",
    androidTitle: "At home on your Android.",
    androidDesc:
      "For phones and tablets running Android 10 or later. Find the current APK and release notes on GitHub Releases.",
    androidFoot: "APK distributed through GitHub Releases",
    webTitle: "Or start in your browser.",
    webDesc:
      "Explore the separate web app without installing an APK. For actual score following, allow microphone access when your browser asks.",
    webFoot: "Explore the separate web repository ↗",
    releaseNote:
      "Features on this page describe the current Android main branch. Check release notes for the capabilities of each APK. The first launch may take a few seconds to initialize the notation engine and soundfont.",
    faqTag: "BEFORE YOU BEGIN",
    faqTitle: "A few notes before you play.",
    faqIntro:
      "Find installation details and known limitations in the project README.",
    readDocs: "Read the documentation",
    faq1q: "Can PDFs and photos follow my playing?",
    faq1a:
      "PDFs and photos use timed scrolling: arrange pages, set the total duration and adjust the speed. Microphone-based note matching and score following work with structured MIDI and MusicXML scores. The project does not convert photographed or PDF notation into recognized notes.",
    faq2q: "What affects recognition quality?",
    faq2a:
      "Score following relies on real microphone input. Ambient noise, the device microphone, volume and the music being played can all affect results. Test on a physical device rather than relying on an emulator. No recognition accuracy is guaranteed across all environments.",
    faq3q: "Why does the first launch take time?",
    faq3a:
      "The first cold start initializes WebView and decodes the sampled piano soundfont. This can take several seconds on some devices, and longer on emulators. The app offers a retry if loading times out; later launches are usually faster.",
    faq4q: "Does this website use my microphone?",
    faq4a:
      "No. The illustration on this page is a visual animation over a real screenshot. It plays no audio and captures no sound. Use the Android or separate web app to try actual recognition.",
    faq5q: "Can I contribute or modify the project?",
    faq5a:
      "The source uses the BSD 3-Clause license. Explore the code, report issues or contribute improvements. Redistributions must also preserve the applicable licenses and credits for third-party resources, including alphaTab, Bravura and the soundfonts.",
    closingTag: "A LITTLE LESS FRICTION. A LITTLE MORE MUSIC.",
    closingTitle: "The next phrase is yours.",
    closingDesc: "A quiet companion. A little more room for music.",
    closingAction: "Begin a practice session",
    footerTag: "An open-source companion for piano practice.",
    feedback: "Feedback",
    privacy: "Privacy policy",
    credits: "Notation powered by alphaTab",
    themePrefix: "Theme: ",
    themeSystem: "System",
    themeLight: "Light",
    themeDark: "Dark",
    themeNext: "Switch to",
    title: "PianoScoreFollower — Stay in the music. Let the score follow.",
    description:
      "An open-source piano practice companion for Android. Real-time score following, automatic page turns, scrolling PDFs and photos, and pitch calibration. A separate web app is also available.",
  },
};
let language = document.documentElement.lang === "en" ? "en" : "zh";
let preference = document.documentElement.dataset.preference;
let selectedScreen = "notation";
const systemTheme = matchMedia("(prefers-color-scheme: dark)"),
  themeOrder = ["system", "light", "dark"],
  themeKeys = { system: "themeSystem", light: "themeLight", dark: "themeDark" };
function save(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {}
}
function applyTheme() {
  const resolved =
    preference === "system"
      ? systemTheme.matches
        ? "dark"
        : "light"
      : preference;
  document.documentElement.dataset.theme = resolved;
  document.documentElement.dataset.preference = preference;
  document.querySelector('meta[name="theme-color"]').content =
    resolved === "dark" ? "#17151c" : "#f7f6f2";
  const t = translations[language],
    next = themeOrder[(themeOrder.indexOf(preference) + 1) % 3],
    label = `${t.themePrefix}${t[themeKeys[preference]]}. ${t.themeNext} ${t[themeKeys[next]]}`;
  const button = document.getElementById("theme");
  button.setAttribute("aria-label", label);
  button.title = label;
}
function applyScreen() {
  const t = translations[language],
    image = document.getElementById("app-screenshot");
  image.src = `assets/${selectedScreen}.webp`;
  image.alt = t[`${selectedScreen}Alt`];
  document.getElementById("screen-description").textContent =
    t[`${selectedScreen}Desc`];
  document
    .getElementById("screen-panel")
    .setAttribute("aria-labelledby", `tab-${selectedScreen}`);
  document.querySelectorAll("[data-screen]").forEach((button) => {
    const active = button.dataset.screen === selectedScreen;
    button.setAttribute("aria-selected", String(active));
    button.tabIndex = active ? 0 : -1;
  });
}
let playing = false,
  demoStep = 0,
  demoTimer = null;
const sequence = [
  "C4",
  "D4",
  "F#4",
  "G4",
  "B4",
  "C5",
  "E4",
  "F#4",
  "G4",
  "B4",
  "C5",
  "D5",
];
function updateDemo() {
  const button = document.getElementById("demo-play");
  button.setAttribute("aria-pressed", String(playing));
  button.querySelector(".play-icon").textContent = playing ? "Ⅱ" : "▶";
  button.querySelector("[data-i18n]").textContent =
    translations[language][playing ? "pauseDemo" : "playDemo"];
  document.getElementById("demo-note").textContent = sequence[demoStep];
  document.getElementById("score-cursor").style.left =
    `${12 + demoStep * 7.2}%`;
  document
    .querySelectorAll("[data-note]")
    .forEach((key) =>
      key.classList.toggle("active", key.dataset.note === sequence[demoStep]),
    );
}
function stopDemo() {
  playing = false;
  clearInterval(demoTimer);
  demoTimer = null;
  updateDemo();
}
function applyLanguage() {
  const t = translations[language];
  document.documentElement.lang = language === "zh" ? "zh-CN" : "en";
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    el.textContent = t[el.dataset.i18n];
    if (t[el.dataset.i18n].includes("\n")) el.style.whiteSpace = "pre-line";
  });
  document
    .querySelectorAll("[data-label]")
    .forEach((el) => el.setAttribute("aria-label", t[el.dataset.label]));
  document
    .querySelectorAll("[data-alt]")
    .forEach((el) => (el.alt = t[el.dataset.alt]));
  const languageButton = document.getElementById("language");
  languageButton.textContent = language === "zh" ? "EN" : "中文";
  languageButton.setAttribute(
    "aria-label",
    language === "zh" ? "Switch to English" : "切换至中文",
  );
  document
    .getElementById("menu")
    .setAttribute(
      "aria-label",
      t[
        document.getElementById("menu").getAttribute("aria-expanded") === "true"
          ? "closeMenu"
          : "openMenu"
      ],
    );
  document.title = t.title;
  document.querySelector('meta[name="description"]').content = t.description;
  document.querySelector('meta[property="og:title"]').content = t.title;
  document.querySelector('meta[property="og:description"]').content =
    t.description;
  document.querySelector('meta[property="og:locale"]').content =
    language === "zh" ? "zh_CN" : "en_US";
  document.querySelector('meta[property="og:locale:alternate"]').content =
    language === "zh" ? "en_US" : "zh_CN";
  applyTheme();
  applyScreen();
  updateDemo();
}
document.getElementById("language").addEventListener("click", () => {
  language = language === "zh" ? "en" : "zh";
  save("psf-site-language", language);
  applyLanguage();
});
document.getElementById("theme").addEventListener("click", () => {
  preference = themeOrder[(themeOrder.indexOf(preference) + 1) % 3];
  save("psf-site-theme", preference);
  applyTheme();
});
systemTheme.addEventListener("change", () => {
  if (preference === "system") applyTheme();
});
document.getElementById("demo-play").addEventListener("click", () => {
  if (playing) {
    stopDemo();
    return;
  }
  playing = true;
  updateDemo();
  demoTimer = setInterval(() => {
    demoStep = (demoStep + 1) % sequence.length;
    updateDemo();
  }, 750);
});
document.getElementById("demo-reset").addEventListener("click", () => {
  demoStep = 0;
  stopDemo();
});
document.addEventListener("visibilitychange", () => {
  if (document.hidden) stopDemo();
});
// Stop the illustration when it scrolls out of view; playback always requires a click.
new IntersectionObserver(
  (entries) => {
    if (!entries[0].isIntersecting && playing) stopDemo();
  },
  { threshold: 0 },
).observe(document.querySelector(".score-card"));
document.querySelectorAll("[data-screen]").forEach((button) => {
  button.addEventListener("click", () => {
    selectedScreen = button.dataset.screen;
    applyScreen();
  });
  button.addEventListener("keydown", (event) => {
    if (
      ![
        "ArrowDown",
        "ArrowUp",
        "ArrowRight",
        "ArrowLeft",
        "Home",
        "End",
      ].includes(event.key)
    )
      return;
    event.preventDefault();
    const buttons = [...document.querySelectorAll("[data-screen]")];
    let index = buttons.indexOf(button);
    index =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? buttons.length - 1
          : (index +
              (["ArrowDown", "ArrowRight"].includes(event.key) ? 1 : -1) +
              buttons.length) %
            buttons.length;
    buttons[index].click();
    buttons[index].focus();
  });
});
const menu = document.getElementById("menu"),
  mobileNav = document.getElementById("mobile-nav");
function closeMenu() {
  mobileNav.hidden = true;
  menu.setAttribute("aria-expanded", "false");
  menu.setAttribute("aria-label", translations[language].openMenu);
}
menu.addEventListener("click", () => {
  const expanded = menu.getAttribute("aria-expanded") === "true";
  mobileNav.hidden = expanded;
  menu.setAttribute("aria-expanded", String(!expanded));
  menu.setAttribute(
    "aria-label",
    translations[language][expanded ? "openMenu" : "closeMenu"],
  );
});
mobileNav.addEventListener("click", (event) => {
  const anchor = event.target.closest("a");
  if (!anchor) return;
  closeMenu();
  if (anchor.getAttribute("href").startsWith("#")) {
    const section = document.querySelector(anchor.getAttribute("href"));
    section.tabIndex = -1;
    section.focus({ preventScroll: true });
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !mobileNav.hidden) {
    closeMenu();
    menu.focus();
  }
});
document.addEventListener("click", (event) => {
  if (!event.target.closest(".header")) closeMenu();
});
matchMedia("(min-width: 851px)").addEventListener("change", (event) => {
  if (event.matches) {
    const restore = mobileNav.contains(document.activeElement);
    closeMenu();
    if (restore) document.querySelector(".desktop-nav a").focus();
  }
});
applyLanguage();
