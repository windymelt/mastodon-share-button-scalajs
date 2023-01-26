## Simple Mastodon Share Button

[日本語README](./README%20-ja_JP.md)

![](./preview.png)

### How to use

Paste this code into your HTML:

```html
<a href="#" class="js-mstdn-share-button"></a>
<script src="https://github.com/windymelt/mastodon-share-button-scalajs/releases/download/v0.0.6/mstdn-share.js"></script>
```

Latest version is ![](https://img.shields.io/github/v/release/windymelt/mastodon-share-button-scalajs?display_name=tag)

### Template String

You can use template string for share text:

```html
<a href="#" class="js-mstdn-share-button">Share: {title} {}</a>
```

Currently following placeholders are available:

- `{}` -- for URL
- `{title}` -- for inner text of `title` element

### Build

Use `sbt fastLinkJS` for development build.

Use `sbt fullLinkJS` for release build.
