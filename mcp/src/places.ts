// A counter's real-world face from Google Places: name, address, rating and a photo. The API key
// stays on the server (photos are proxied), lookups are only made for merchants registered on chain,
// and results are cached, so the endpoint can't be used as a free Places proxy.
//
// A match by name is only a guess; PLACE_PINS ("<merchant pda>=<place id>,...") records the place a
// maintainer confirmed for a counter, and the app labels the two differently.

export interface PlaceView {
  pinned: boolean;
  name: string;
  address: string;
  rating?: number;
  ratings?: number;
  mapsUri?: string;
  /** Path on this server for the first photo, if any. */
  photo?: string;
}

interface GPlace {
  id: string;
  displayName?: { text: string };
  formattedAddress?: string;
  rating?: number;
  userRatingCount?: number;
  googleMapsUri?: string;
  photos?: { name: string }[];
}

const FIELDS = ['id', 'displayName', 'formattedAddress', 'rating', 'userRatingCount', 'googleMapsUri', 'photos'];
const PHOTO = /^places\/[\w-]+\/photos\/[\w-]+$/;

export function parsePins(spec: string | undefined): Map<string, string> {
  const out = new Map<string, string>();
  for (const pair of (spec ?? '').split(',')) {
    const [k, v] = pair.split('=').map((s) => s.trim());
    if (k && v) out.set(k, v);
  }
  return out;
}

export class Places {
  private cache = new Map<string, { at: number; view: PlaceView | null }>();
  private photos = new Set<string>();

  constructor(
    private apiKey: string,
    private pins: Map<string, string>,
    private http: typeof fetch = fetch,
    private ttlMs = 24 * 3600_000,
  ) {}

  private headers(fields: string) {
    return { 'content-type': 'application/json', 'X-Goog-Api-Key': this.apiKey, 'X-Goog-FieldMask': fields };
  }

  private view(p: GPlace, pinned: boolean): PlaceView {
    const photo = p.photos?.[0]?.name;
    if (photo) this.photos.add(photo);
    return {
      pinned,
      name: p.displayName?.text ?? '',
      address: p.formattedAddress ?? '',
      rating: p.rating,
      ratings: p.userRatingCount,
      mapsUri: p.googleMapsUri,
      photo: photo ? `/places/photo?ref=${encodeURIComponent(photo)}` : undefined,
    };
  }

  /** The place for a registered merchant: its pin when there is one, else the best match for its name. */
  async forMerchant(merchant: string, name: string): Promise<PlaceView | null> {
    const hit = this.cache.get(merchant);
    if (hit && Date.now() - hit.at < this.ttlMs) return hit.view;
    const pin = this.pins.get(merchant);
    let view: PlaceView | null;
    if (pin) {
      const res = await this.http(`https://places.googleapis.com/v1/places/${encodeURIComponent(pin)}`, { headers: this.headers(FIELDS.join(',')) });
      if (!res.ok) throw new Error(`places ${res.status}`);
      view = this.view((await res.json()) as GPlace, true);
    } else {
      const res = await this.http('https://places.googleapis.com/v1/places:searchText', {
        method: 'POST',
        headers: this.headers(FIELDS.map((f) => `places.${f}`).join(',')),
        body: JSON.stringify({ textQuery: name, pageSize: 1 }),
      });
      if (!res.ok) throw new Error(`places ${res.status}`);
      const first = ((await res.json()) as { places?: GPlace[] }).places?.[0];
      view = first ? this.view(first, false) : null;
    }
    this.cache.set(merchant, { at: Date.now(), view });
    return view;
  }

  /** Photo bytes for a reference this server handed out (never an arbitrary one). */
  async photo(ref: string): Promise<{ type: string; body: Buffer } | null> {
    if (!PHOTO.test(ref) || !this.photos.has(ref)) return null;
    const res = await this.http(`https://places.googleapis.com/v1/${ref}/media?maxWidthPx=640&key=${this.apiKey}`);
    if (!res.ok) return null;
    return { type: res.headers.get('content-type') ?? 'image/jpeg', body: Buffer.from(await res.arrayBuffer()) };
  }
}
