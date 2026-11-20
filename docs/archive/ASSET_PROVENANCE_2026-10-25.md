# UA-Cast artwork provenance record

On 2026-10-25 the publisher answered the specific question about the main UA-Cast
logo and application background: **«Створені мною або ШІ»** (created by me or AI).
This is the publisher's declaration, not an independently verified copyright or
trademark clearance. No contrary evidence was found in the audit.

Scope: the application wallpaper and the UA-Cast launcher, splash and television
banner variants derived from that branding under `app/src/main/res/`. This does
not assert rights to channel logos supplied by users, screenshots of third-party
programmes, future store marketing assets or the licensed fonts/components.

The TV wordmark uses Roboto with its Apache-2.0 notice and modification record in
`app/src/main/assets/licenses/tv-wordmark-roboto.txt`. Other component notices stay
with their respective files. The application does not bundle channel logo packs.

Retain original design files and, where applicable, generation history and the
tool's applicable commercial-use terms. These are useful provenance evidence;
the declaration does not establish exclusive rights in all AI output or resolve
possible trademark similarity or codec patents. Those questions require a
separate assessment in the intended distribution markets.

## Shared vector refresh — 2026-11-03

The current screen/play/transmission geometry is authored in this project at
`design/brand/mark.svg`. It is the editable source for phone adaptive/monochrome
icons, splash and the TV banner mark. No external artwork or additional font was
downloaded for this refresh. Existing attributed Roboto wordmark contours and
their licence notice remain unchanged. The older `design/ic_launcher-master.png`
is retained as historical design input, not as the current generator source.

`scripts/generate-brand-vectors.py` exports Android vector resources;
`scripts/render-brand-icons.cjs` renders legacy icon candidates using Sharp as a
development tool only. TV pixels are exported through the Android renderer and
checked against the packaged banner. This provenance statement does not add a
trademark clearance or any assertion about third-party channel logos.
