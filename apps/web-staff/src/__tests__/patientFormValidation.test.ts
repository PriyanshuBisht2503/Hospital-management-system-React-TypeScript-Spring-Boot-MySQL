import { describe, it, expect } from 'vitest';
import { createPatientSchema, isValidEmail, isValidDateOfBirth } from '../pages/NewPatientPage';

describe('NewPatientPage: form validation', () => {
  it('requires a full name', () => {
    expect(createPatientSchema.safeParse({ fullName: '', phone: '+971501234567' }).success).toBe(false);
  });

  it('requires at least one of phone or email', () => {
    expect(createPatientSchema.safeParse({ fullName: 'John Smith', phone: '', email: '' }).success).toBe(false);
  });

  it('accepts a patient with a phone number only', () => {
    expect(createPatientSchema.safeParse({ fullName: 'John Smith', phone: '+971501234567', email: '' }).success).toBe(true);
  });

  it('accepts a patient with an email only', () => {
    expect(createPatientSchema.safeParse({ fullName: 'John Smith', phone: '', email: 'john@example.com' }).success).toBe(true);
  });

  it('rejects an invalid email', () => {
    expect(createPatientSchema.safeParse({ fullName: 'John Smith', email: 'not-an-email' }).success).toBe(false);
  });

  it('rejects an invalid date of birth (impossible calendar date)', () => {
    expect(
      createPatientSchema.safeParse({ fullName: 'John Smith', phone: '+971501234567', dateOfBirth: '2026-02-30' }).success,
    ).toBe(false);
  });

  it('rejects a future date of birth', () => {
    expect(
      createPatientSchema.safeParse({ fullName: 'John Smith', phone: '+971501234567', dateOfBirth: '2999-01-01' }).success,
    ).toBe(false);
  });

  it('accepts MALE, FEMALE and OTHER gender and rejects an invalid one', () => {
    expect(createPatientSchema.safeParse({ fullName: 'John Smith', phone: '+971', gender: 'MALE' }).success).toBe(true);
    expect(createPatientSchema.safeParse({ fullName: 'John Smith', phone: '+971', gender: 'FEMALE' }).success).toBe(true);
    expect(createPatientSchema.safeParse({ fullName: 'John Smith', phone: '+971', gender: 'OTHER' }).success).toBe(true);
    expect(createPatientSchema.safeParse({ fullName: 'John Smith', phone: '+971', gender: 'ROBOT' }).success).toBe(false);
  });

  it('isValidEmail and isValidDateOfBirth helpers behave correctly', () => {
    expect(isValidEmail('a@b.com')).toBe(true);
    expect(isValidEmail('bad')).toBe(false);
    expect(isValidDateOfBirth('1990-05-01')).toBe(true);
    expect(isValidDateOfBirth('2999-01-01')).toBe(false);
    expect(isValidDateOfBirth('2026-02-30')).toBe(false);
  });
});
