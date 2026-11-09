# Contacts Only

Contacts Only is an Android app that blocks incoming calls from numbers you
haven’t saved in your contacts.

It was created because built-in spam filtering wasn’t enough to stop unwanted
calls. The rule is simple: saved contacts ring normally, and other visible
phone numbers are rejected. You decide who can reach you by saving their number.

## What it does

- Rejects calls from unsaved numbers when blocking is on.
- Lets you pause blocking with a switch, without uninstalling the app.
- Remembers your choice when you close the app or restart your phone.
- Keeps rejected calls in your phone’s blocked-call history.

Text messages and your existing messaging app are unchanged.

## Getting started

1. Install and open **Contacts Only**.
2. Turn on **Block calls from unsaved numbers**.
3. When Android asks, choose **Contacts Only** as your caller ID and spam app.

Turn the switch off when you’re expecting a call from someone you haven’t saved,
such as a delivery driver, doctor’s office or prospective employer. Turn it back
on when you want to block unsaved callers again.

Supported on **Android 15 and later**.

## Privacy

Android checks whether the caller is in your contacts. Contacts Only does not
request access to your contacts, call history, messages or the internet.
There are no accounts, ads or analytics.

## Things to know

- **Hidden or withheld numbers:** Android does not send these to the app. Use
  your phone’s separate hidden-number blocking setting for them.
- **Spoofed numbers:** A caller pretending to be one of your saved contacts may
  still get through.
- **Other blocking settings:** Your phone and carrier can still block calls
  when Contacts Only is off.

### Moving from version 1.0 or 1.1.0

Version 1.2.0 installs as a separate app. Install it, enable blocking and choose
it as your caller ID and spam app. Android will then use the new app, and you
can uninstall the old Contacts Only.

## More information

[Architecture](docs/ARCHITECTURE.md) · [Testing](docs/TESTING.md) ·
[Development](docs/DEVELOPMENT.md)

## License

[MIT](LICENSE).
