// Driver headshot URLs and metadata from OpenF1.
// The formula1.com image CDN follows a predictable pattern.
// We embed the current grid's data and fall back gracefully for historical drivers.

interface DriverMeta {
  headshotUrl: string;
  number: number | null;
  countryCode: string | null;
}

// Mapping from 3-letter driver code to their metadata
const DRIVER_META: Record<string, DriverMeta> = {
  NOR: { number: 1, countryCode: "GBR", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LANNOR01_Lando_Norris/lannor01.png.transform/1col/image.png" },
  VER: { number: 3, countryCode: "NLD", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/M/MAXVER01_Max_Verstappen/maxver01.png.transform/1col/image.png" },
  BOR: { number: 5, countryCode: "BRA", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/G/GABBOR01_Gabriel_Bortoleto/gabbor01.png.transform/1col/image.png" },
  HAD: { number: 6, countryCode: "NZL", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/I/ISAHAD01_Isack_Hadjar/isahad01.png.transform/1col/image.png" },
  GAS: { number: 10, countryCode: "FRA", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/P/PIEGAS01_Pierre_Gasly/piegas01.png.transform/1col/image.png" },
  PER: { number: 11, countryCode: "MEX", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/S/SERPER01_Sergio_Perez/serper01.png.transform/1col/image.png" },
  ANT: { number: 12, countryCode: "ITA", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/K/ANDANT01_Kimi_Antonelli/andant01.png.transform/1col/image.png" },
  ALO: { number: 14, countryCode: "ESP", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/F/FERALO01_Fernando_Alonso/feralo01.png.transform/1col/image.png" },
  LEC: { number: 16, countryCode: "MON", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/C/CHALEC01_Charles_Leclerc/chalec01.png.transform/1col/image.png" },
  STR: { number: 18, countryCode: "CAN", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LANSTR01_Lance_Stroll/lanstr01.png.transform/1col/image.png" },
  ALB: { number: 23, countryCode: "THA", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/A/ALEALB01_Alexander_Albon/alealb01.png.transform/1col/image.png" },
  HUL: { number: 27, countryCode: "DEU", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/N/NICHUL01_Nico_Hulkenberg/nichul01.png.transform/1col/image.png" },
  LAW: { number: 30, countryCode: "GBR", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LIALAW01_Liam_Lawson/lialaw01.png.transform/1col/image.png" },
  LIN: { number: 41, countryCode: "FIN", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/A/ARVLIN01_Arvid_Lindblad/arvlin01.png.transform/1col/image.png" },
  COL: { number: 43, countryCode: "FRA", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/F/FRACOL01_Franco_Colapinto/fracol01.png.transform/1col/image.png" },
  HAM: { number: 44, countryCode: "GBR", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/L/LEWHAM01_Lewis_Hamilton/lewham01.png.transform/1col/image.png" },
  CAM: { number: 51, countryCode: "USA", headshotUrl: "" },
  SAI: { number: 55, countryCode: "ESP", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/C/CARSAI01_Carlos_Sainz/carsai01.png.transform/1col/image.png" },
  RUS: { number: 63, countryCode: "GBR", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/G/GEORUS01_George_Russell/georus01.png.transform/1col/image.png" },
  BOT: { number: 77, countryCode: "FIN", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/V/VALBOT01_Valtteri_Bottas/valbot01.png.transform/1col/image.png" },
  PIA: { number: 81, countryCode: "AUS", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/O/OSCPIA01_Oscar_Piastri/oscpia01.png.transform/1col/image.png" },
  BEA: { number: 87, countryCode: "USA", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/O/OLIBEA01_Oliver_Bearman/olibea01.png.transform/1col/image.png" },
  // Historical drivers commonly looked up
  RIC: { number: 3, countryCode: "AUS", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/D/DANRIC01_Daniel_Ricciardo/danric01.png.transform/1col/image.png" },
  TSU: { number: 22, countryCode: "JPN", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/Y/YUKTSU01_Yuki_Tsunoda/yuktsu01.png.transform/1col/image.png" },
  MAG: { number: 20, countryCode: "DNK", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/K/KEVMAG01_Kevin_Magnussen/kevmag01.png.transform/1col/image.png" },
  ZHO: { number: 24, countryCode: "CHN", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/G/GUAZHO01_Guanyu_Zhou/guazho01.png.transform/1col/image.png" },
  OCO: { number: 31, countryCode: "FRA", headshotUrl: "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/E/ESTOCO01_Esteban_Ocon/estoco01.png.transform/1col/image.png" },
};

export function getDriverHeadshot(code: string | null): string | null {
  if (!code) return null;
  const meta = DRIVER_META[code.toUpperCase()];
  return meta?.headshotUrl || null;
}

export function getDriverNumber(code: string | null): number | null {
  if (!code) return null;
  return DRIVER_META[code.toUpperCase()]?.number ?? null;
}

export function getDriverCountry(code: string | null): string | null {
  if (!code) return null;
  return DRIVER_META[code.toUpperCase()]?.countryCode ?? null;
}
