import { describe, it, expect, beforeEach } from 'vitest';
import { tokenStore } from '../../api/tokenStore';

describe('tokenStore', () => {
  beforeEach(() => {
    tokenStore.clear();
  });

  it('returns null when no token has been set', () => {
    expect(tokenStore.get()).toBeNull();
    expect(tokenStore.hasToken()).toBe(false);
  });

  it('stores and retrieves an access token', () => {
    tokenStore.set('my.test.token');
    expect(tokenStore.get()).toBe('my.test.token');
    expect(tokenStore.hasToken()).toBe(true);
  });

  it('clears the token', () => {
    tokenStore.set('my.test.token');
    tokenStore.clear();
    expect(tokenStore.get()).toBeNull();
    expect(tokenStore.hasToken()).toBe(false);
  });

  it('overwrites an existing token with a new one', () => {
    tokenStore.set('first.token');
    tokenStore.set('second.token');
    expect(tokenStore.get()).toBe('second.token');
  });

  it('does not persist to localStorage', () => {
    tokenStore.set('sensitive.token');
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('accessToken')).toBeNull();
  });
});
