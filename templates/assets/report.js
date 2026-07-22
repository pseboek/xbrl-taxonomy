(() => {
    const sections = document.querySelectorAll("section");
    sections.forEach((section, index) => {
        section.style.opacity = "0";
        section.style.transform = "translateY(8px)";
        setTimeout(() => {
            section.style.transition = "opacity 280ms ease, transform 280ms ease";
            section.style.opacity = "1";
            section.style.transform = "translateY(0)";
        }, 60 * (index + 1));
    });
})();
