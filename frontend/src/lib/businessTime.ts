import { Temporal } from '@js-temporal/polyfill'

export function businessDate(timeZone: string): string {
  return Temporal.Now.plainDateISO(timeZone).toString()
}

export function calendarRange(date: string, timeZone: string, view: 'day' | 'week' = 'day') {
  const selected = Temporal.PlainDate.from(date)
  const start = view === 'week' ? selected.subtract({ days: selected.dayOfWeek - 1 }) : selected
  const end = start.add({ days: view === 'week' ? 7 : 1 })
  return {
    from: start.toZonedDateTime(timeZone).toInstant().toString(),
    to: end.toZonedDateTime(timeZone).toInstant().toString(),
  }
}

export function possibleStarts(date: string, time: string, timeZone: string) {
  const local = Temporal.PlainDateTime.from(`${date}T${time}`)
  const earlier = local.toZonedDateTime(timeZone, { disambiguation: 'earlier' })
  if (!earlier.toPlainDateTime().equals(local)) return []
  const later = local.toZonedDateTime(timeZone, { disambiguation: 'later' })
  const choices = earlier.equals(later) ? [earlier] : [earlier, later]
  return choices.map(choice => ({ instant: choice.toInstant().toString(), offset: choice.offset }))
}

export function timeFormatter(instant: string, timeZone: string): Intl.DateTimeFormat {
  const day = Temporal.Instant.from(instant).toZonedDateTimeISO(timeZone)
  return new Intl.DateTimeFormat('tr-TR', {
    hour: '2-digit', minute: '2-digit', timeZone,
    timeZoneName: day.hoursInDay > 24 ? 'shortOffset' : undefined,
  })
}
