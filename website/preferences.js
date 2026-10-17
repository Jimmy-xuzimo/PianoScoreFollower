(() => {
  let theme = "system",
    language = navigator.language.toLowerCase().startsWith("zh") ? "zh" : "en";
  try {
    const savedTheme = localStorage.getItem("psf-site-theme"),
      savedLanguage = localStorage.getItem("psf-site-language");
    if (["system", "light", "dark"].includes(savedTheme)) theme = savedTheme;
    if (["zh", "en"].includes(savedLanguage)) language = savedLanguage;
  } catch {}
  document.documentElement.dataset.preference = theme;
  document.documentElement.dataset.theme =
    theme === "system"
      ? matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light"
      : theme;
  document.documentElement.lang = language === "zh" ? "zh-CN" : "en";
})();
