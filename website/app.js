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
    heroTag: "智能琴谱 1.0 · Android 正式版",
    hero1: "让乐谱，",
    hero2: "跟上你的演奏。",
    heroDesc:
      "双手留给琴键，目光留给旋律。导入你的乐谱，让实时跟谱与自动翻页，陪你完成下一次练习。",
    download: "下载 Android 版",
    tryWeb: "体验网页版",
    heroNote: "开源 · Android 10+ · 钢琴练习",
    demoTitle: "跟谱交互示意",
    scoreAlt: "C 大调练习曲 · Android 1.0 白色琴谱",
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
      "通过麦克风或 USB MIDI 获取演奏，用完整音高、起音与速度对齐 MIDI / MusicXML。乐谱约束预测让光标连续前进，低置信度时暂停翻页。",
    followFoot: "钢琴模型 · 序列对齐 · 预测跟随",
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
    screenSettings: "设置与关于",
    screenSettingsSub: "开源协议、来源与项目链接。",
    notationDesc:
      "曲库保存 MIDI / MusicXML 与练习位置；内置曲目按需打开，包含《你离开的事实》。点按谱面定位，支持音轨与谱表选择。",
    playingDesc:
      "独立深色演奏背景搭配白色琴谱，五线谱与上下双行简谱自由切换。控制条可隐藏，主要空间留给琴谱。支持音高与节奏评分。",
    tunerDesc:
      "先弹中央 C（C4）建立基准，再查看当前音符与音分偏差。建议在真实设备和合适的环境音量下使用。",
    settingsDesc:
      "设置中选择主题、壁纸取色；关于页提供开源协议全文、项目来源、GitHub 和介绍网页链接。",
    notationAlt: "Android 应用的乐谱阅读界面",
    playingAlt: "Android 应用的乐谱播放界面",
    tunerAlt: "Android 应用的音准校准界面",
    settingsAlt: "Android 1.0 关于与开源项目界面",
    openWeb: "打开独立网页版",
    screenshotNote: "真实应用截图 · 界面语言以应用为准",
    stepsTag: "简单开始，慢慢进步",
    stepsTitle: "三步，进入你的练习时间。",
    step1Title: "带上你的乐谱",
    step1Desc:
      "导入 MIDI / MusicXML，或用 PDF 与照片建立滚动谱。也可以从应用内置示例开始。",
    step2Title: "选一种练习方式",
    step2Desc:
      "选择听音或 USB MIDI 跟谱，也可录制 MIDI 并导出；PDF 与照片使用定时滚动。",
    step3Title: "把注意力交给音乐",
    step3Desc:
      "跟随光标、播放乐谱，或让页面按节奏滚动。随时暂停、调整，再继续练习。",
    craftTag: "开放的代码，认真的细节",
    craftTitle: "音乐之外，\n也值得仔细打磨。",
    craftDesc:
      "Google 官方 Compose Material 3、alphaTab、Magenta 实时钢琴模型和 Parangonar 在线对齐协作。输入、模型与评分都在设备本地处理。",
    exploreSource: "探索 GitHub 源码",
    craftUi: "Material 3 界面，横竖屏适配与本地偏好保存。",
    craftScore: "在 WebView 中完成乐谱排版与声音合成。",
    craftDsp: "Magenta / LiteRT 钢琴音符推理、Parangonar 对齐与乐谱约束预测。",
    startTag: "下一首，从这里开始",
    startTitle: "选择你的练习入口。",
    startDesc: "Android 1.0 已重构；独立网页版单独维护，不代表相同算法或功能。",
    androidTitle: "在你的 Android 设备上。",
    androidDesc:
      "Android 10 及以上手机与平板。下载 v1.0.0，查看正式版变更、已知限制与测试报告。",
    androidFoot: "APK 通过 GitHub Releases 分发",
    webTitle: "也可以，先打开浏览器。",
    webDesc:
      "无需安装 APK，即可体验独立网页版本。在实际跟谱时，根据浏览器提示允许麦克风访问。",
    webFoot: "查看网页版独立仓库 ↗",
    releaseNote:
      "1.0.0：深色演奏、白色琴谱、五线谱／简谱切换、音高与节奏评分、USB MIDI 录制和导出。定位回归已达 97% 以上；重弹恢复仍有延迟，实体麦克风和 USB 硬件需按设备验证。",
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
      "跟谱首次准备会加载本地钢琴模型，不等待播放音源。示范播放的钢琴音源按需加载，失败可重试。",
    faq4q: "这个介绍网站会访问麦克风吗？",
    faq4a:
      "不会。本页的跟谱演示只是基于真实截图的视觉动画，不播放音频，也不采集声音。要体验实际识别，请使用 Android 应用或独立网页版。",
    faq5q: "可以参与开发或二次修改吗？",
    faq5a:
      "应用原创代码采用 BSD 3-Clause；Magenta、Parangonar、LiteRT 和 AndroidX 为 Apache 2.0，alphaTab 为 MPL 2.0，Bravura 为 SIL OFL，FreePats 音源为 CC0。应用关于页提供协议全文和上游来源。",
    closingTag: "A LITTLE LESS FRICTION. A LITTLE MORE MUSIC.",
    closingTitle: "下一段旋律，留给你。",
    closingDesc: "让工具安静地陪伴，让练习继续发生。",
    closingAction: "开始一次练习",
    footerTag: "一个开源的钢琴练习伴侣。",
    feedback: "反馈建议",
    privacy: "隐私政策",
    credits: "开源署名与第三方协议见应用关于页及仓库",
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
    heroTag: "PIANO SCORE FOLLOWER 1.0 · ANDROID",
    hero1: "Stay in the music.",
    hero2: "Let the score follow.",
    heroDesc:
      "Keep your hands on the keys and your attention on the melody. Bring your scores, follow along, and turn the page without breaking your practice.",
    download: "Get Android app",
    tryWeb: "Try the web app",
    heroNote: "Open source · Android 10+ · Piano practice",
    demoTitle: "Score-following illustration",
    scoreAlt: "C major study on Android 1.0 white score paper",
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
      "Microphone or USB MIDI input is aligned to MIDI / MusicXML using full pitches, onsets and tempo. Score-constrained prediction smooths the cursor; page turns pause when confidence drops.",
    followFoot: "Piano model · Sequence alignment · Predictive following",
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
    screenSettings: "Settings and About",
    screenSettingsSub: "Licenses, credits and project links.",
    notationDesc:
      "A local score library stores MIDI / MusicXML and practice positions. Open built-in examples when needed, including The Truth That You Leave. Tap to seek and select tracks or staves.",
    playingDesc:
      "A dark performance workspace with white score paper, staff / two-row numbered notation, hideable controls, and pitch plus rhythm assessment.",
    tunerDesc:
      "Play middle C (C4) to establish a reference, then inspect the detected note and cents deviation. Use a real device with suitable ambient sound levels.",
    settingsDesc:
      "Choose themes or wallpaper colors in Settings. About provides full licenses, upstream credits, GitHub and website links.",
    notationAlt: "Android app score-reading screen",
    playingAlt: "Android app score-playback screen",
    tunerAlt: "Android app pitch-calibration screen",
    settingsAlt: "Android 1.0 About and open-source credits",
    openWeb: "Open the separate web app",
    screenshotNote: "Actual app screenshots · In-app text shown as captured",
    stepsTag: "A SIMPLE START",
    stepsTitle: "Three steps into practice.",
    step1Title: "Bring a score",
    step1Desc:
      "Import MIDI or MusicXML, create a scrolling score from PDFs and photos, or start with one of the built-in examples.",
    step2Title: "Find your way to practice",
    step2Desc:
      "Choose microphone or USB MIDI following, record MIDI and export, or use timed scrolling for PDF and photos.",
    step3Title: "Give the music your attention",
    step3Desc:
      "Follow the cursor, play back the score, or let pages scroll at a steady pace. Pause, adjust and pick up where you left off.",
    craftTag: "OPEN CODE. THOUGHTFUL DETAILS.",
    craftTitle: "Crafted beyond\nthe notes.",
    craftDesc:
      "Official Google Compose Material 3, alphaTab, Magenta piano transcription and Parangonar alignment work together. Input, inference and assessment stay on the device.",
    exploreSource: "Explore the GitHub source",
    craftUi:
      "Material 3, portrait and landscape layouts, and local preferences.",
    craftScore: "Score rendering and sound synthesis inside a WebView.",
    craftDsp: "Magenta / LiteRT piano inference, Parangonar alignment and score-constrained prediction.",
    startTag: "YOUR NEXT PIECE STARTS HERE",
    startTitle: "Choose your way to practice.",
    startDesc: "Android 1.0 has been rebuilt. The separate web application is maintained independently and does not imply matching algorithms or features.",
    androidTitle: "At home on your Android.",
    androidDesc:
      "For Android 10+ phones and tablets. Download v1.0.0 and read its changes, limitations and test report.",
    androidFoot: "APK distributed through GitHub Releases",
    webTitle: "Or start in your browser.",
    webDesc:
      "Explore the separate web app without installing an APK. For actual score following, allow microphone access when your browser asks.",
    webFoot: "Explore the separate web repository ↗",
    releaseNote:
      "1.0.0 adds a dark performance view, white score paper, staff / numbered notation, pitch and rhythm assessment, USB MIDI recording and export. Fixed regression positioning exceeds 97%; restart recovery still has delays. Physical microphone and USB behavior require device validation.",
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
      "Following loads a local piano model independently from playback. The soundfont loads only when demonstration playback is requested; failures can be retried.",
    faq4q: "Does this website use my microphone?",
    faq4a:
      "No. The illustration on this page is a visual animation over a real screenshot. It plays no audio and captures no sound. Use the Android or separate web app to try actual recognition.",
    faq5q: "Can I contribute or modify the project?",
    faq5a:
      "Original application code is BSD 3-Clause. Magenta, Parangonar, LiteRT and AndroidX use Apache 2.0; alphaTab uses MPL 2.0; Bravura uses SIL OFL; the FreePats piano is CC0. Full licenses and upstream credits are available in About.",
    closingTag: "A LITTLE LESS FRICTION. A LITTLE MORE MUSIC.",
    closingTitle: "The next phrase is yours.",
    closingDesc: "A quiet companion. A little more room for music.",
    closingAction: "Begin a practice session",
    footerTag: "An open-source companion for piano practice.",
    feedback: "Feedback",
    privacy: "Privacy policy",
    credits: "Open-source credits and licenses are in About and the repository",
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
