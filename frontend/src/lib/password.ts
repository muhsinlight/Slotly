export function passwordSizeError(password: string): string {
  return new TextEncoder().encode(password).byteLength > 72
    ? 'Parola UTF-8 ile en fazla 72 bayt olabilir. Türkçe harfler ve emojiler birden fazla bayt kullanır.'
    : ''
}
