// Small progressive enhancements. Every page works without this script; it only adds conveniences.
(function () {
    "use strict";

    // Menus (<details class="menu">) and dialogs (<dialog>) opened by [data-dialog] / closed by [data-close].
    document.addEventListener("click", function (event) {
        var opener = event.target.closest("[data-dialog]");
        if (opener) {
            var menu = opener.closest("details");
            if (menu) menu.open = false;
            var dialog = document.getElementById(opener.dataset.dialog);
            if (dialog) dialog.showModal();
            return;
        }
        var closer = event.target.closest("[data-close]");
        if (closer) {
            closer.closest("dialog").close();
            return;
        }
        // Clicking anywhere else closes any open menu.
        document.querySelectorAll("details.menu[open]").forEach(function (menu) {
            if (!menu.contains(event.target)) menu.open = false;
        });
    });

    // Escape closes an open menu (dialogs already handle Escape natively).
    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") return;
        document.querySelectorAll("details.menu[open]").forEach(function (menu) {
            menu.open = false;
            menu.querySelector("summary").focus();
        });
    });

    // Forms that ask first (kept out of inline handlers so the Content-Security-Policy can block inline script).
    document.querySelectorAll("form[data-confirm]").forEach(function (form) {
        form.addEventListener("submit", function (event) {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });

    // Settings forms save as soon as a switch changes.
    document.querySelectorAll("form[data-autosubmit]").forEach(function (form) {
        form.addEventListener("change", function () { form.submit(); });
    });

    // New-playlist form: keep the size limits in step with how many people are picked
    // (1 song per person up to 50 each) and stop people picking more than allowed.
    var form = document.querySelector("form.blend-form");
    if (!form) return;
    var size = form.querySelector("input[name=size]");
    var hint = document.getElementById("size-hint");
    var perPerson = Number(form.dataset.perPerson);
    var maxPartners = Number(form.dataset.maxPartners);
    var boxes = Array.prototype.slice.call(form.querySelectorAll("input[name=partnerIds]"));

    function update() {
        var picked = boxes.filter(function (b) { return b.checked; }).length;
        boxes.forEach(function (b) { b.disabled = !b.checked && picked >= maxPartners; });
        var people = picked + 1;
        var min = people, max = people * perPerson;
        size.min = min;
        size.max = max;
        if (Number(size.value) < min) size.value = min;
        if (Number(size.value) > max) size.value = max;
        hint.textContent = people + (people === 1 ? " person" : " people") + ": " + min + " to " + max + " songs";
    }

    boxes.forEach(function (b) { b.addEventListener("change", update); });
    update();
})();
