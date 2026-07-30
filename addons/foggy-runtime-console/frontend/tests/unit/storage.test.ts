import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearConsoleSession,
  consoleStorageKeys,
  readDataAuthorization,
  readRuntimeToken,
  setDataAuthorization,
  writeRuntimeToken
} from '@/api/storage'

describe('Console session storage boundary', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    clearConsoleSession()
  })

  it('stores the Runtime token only in namespaced sessionStorage', () => {
    writeRuntimeToken('runtime-token')

    expect(readRuntimeToken()).toBe('runtime-token')
    expect(sessionStorage.getItem(consoleStorageKeys.token)).toBe('runtime-token')
    expect(localStorage.length).toBe(0)
  })

  it('keeps data-plane authorization volatile and clears both credentials', () => {
    writeRuntimeToken('runtime-token')
    setDataAuthorization('Bearer data-token')
    expect(readDataAuthorization()).toBe('Bearer data-token')

    clearConsoleSession()

    expect(readRuntimeToken()).toBeNull()
    expect(readDataAuthorization()).toBeNull()
  })
})
