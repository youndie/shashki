---
id: B-67
title: "The desktop build can enter a screen and not leave it"
status: open
priority: P2
size: S
stage: stage-6-what-running-it-said
---

# B-67 — The desktop build can enter a screen and not leave it

In the window, open the driver's *documents* and there is no way out: no back control on the screen,
`Escape` does nothing, and the shift is unreachable until the process is restarted. The same shape is
in the rider — *trips* is reached from the order bar's overflow and left the same way, which is to
say not at all.

**It is the address bar's absence, and that is deliberate for the right reason.** `NoAddressBar` is
the desktop `actual`: a window has no history and no URL, so B-28's browser-side back — which is real
and tested — has nothing to drive here. What was not noticed is that nothing else drives it either,
so the back stack the navigation is built on is push-only in a window.

- **The desktop build exists to be looked at**, which is exactly the use that this breaks: it is how
  a screen is reviewed without a browser, and a reviewer who opens D1 has to restart the application
  to see D2 again.
- **A control on the screen, not a key.** `Escape` is a dismissal and these are pages; the kit's own
  answer is the app bar, which every one of these screens already has. What is missing is one back
  affordance on the screens that are pushed rather than started at.
- The rejected alternative is making `NoAddressBar` remember a stack of its own. That is a browser's
  history reimplemented for a window that has no address to show, and the back stack already exists —
  it just has nobody to pop it.
- Deliberately **not** covered: the browser. There, back works and is tested; this is the window's
  half.

- AC: from any pushed screen in either window, one visible control returns to the screen it was
  pushed from, and the shift or the class picker is reachable without restarting.
- AC: a golden shows the control where it lands, so the kit's app bar rule is checked rather than
  assumed.
- Anchors: `shared-ui/src/desktopMain/kotlin/io/github/youndie/shashki/ui/nav/AddressBar.desktop.kt`,
  `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/App.kt`,
  `rider/src/commonMain/kotlin/io/github/youndie/shashki/rider/App.kt`
