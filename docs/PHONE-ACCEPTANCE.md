# Physical-phone acceptance record

Device: Samsung SM_S938U. The original 1.0 was used successfully by the owner.

Installed November 4, 2026: the released 1.1.0 APK (code 2), SHA-256
`fae31cd05479d4cadeb34c4bf956e8b62576da9ceaf41076c16e04f513625c40`, signed with
the release key and rotation lineage. Observed: updated the earlier debug-signed
1.1.0 test build in place; Android lists the debug certificate as a past signer;
first-install time unchanged since 1.0; call-screening role retained. The owner
chose to publish before the toggle checks below. That install uses the old
application ID, `com.ben.contactsonly`; 1.2.0 (`com.contactsonly.app`) will
install alongside it, and the call-screening role moves when approved.

The following are **pending** for the new toggle. Record the date, OS/build,
candidate APK hash and observed result. Do not mark them passed from emulator
results. Calls require an owner-controlled second phone/caller; the agent has
not placed any real calls or sent messages.

| Check | Expected observation | Observed result |
| --- | --- | --- |
| New ID install | 1.2.0 installs alongside the old app; approving it moves the call-screening role; blocking is on | Pending |
| On: saved contact calls | Phone rings normally | Pending |
| On: unsaved caller calls | Rejected; visible in system blocked history | Pending |
| Off: same unsaved caller | Rings, subject to other phone/carrier settings | Pending |
| Off: close/reopen | Switch stays off | Pending |
| Off: restart phone when convenient | Switch stays off; caller can still ring | Pending |
| On again | Unsaved caller rejected without reinstalling | Pending |
| Android role revoked/cancelled | UI shows off; cancelling approval keeps it off | Pending |
| Hidden caller | Behavior follows the phone's separate hidden-number setting | Pending |
| Contact saved in another format | A contact saved without +1 (or with it) still rings | Pending |
| Secure Folder or work contact | Record whether a caller saved only there rings or is rejected | Pending |
| Existing texting/outgoing calls | Continue using the existing apps | Pending |
| Large text/TalkBack | Switch label, state, focus and actions understandable | Pending |

An absent caller ID and an unsaved visible number are different cases. An
emulator cannot settle Samsung/Verizon behavior, spam settings or real voicemail
routing. Leave any unexercised cell pending and preserve unexpected results.
