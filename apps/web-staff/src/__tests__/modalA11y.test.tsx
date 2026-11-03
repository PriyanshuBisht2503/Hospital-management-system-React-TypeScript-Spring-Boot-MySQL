// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest';
import { useState } from 'react';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { Modal } from '@hms/ui';

afterEach(() => {
  cleanup();
});

const Harness = () => {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button onClick={() => setOpen(true)}>Open dialog</button>
      <Modal isOpen={open} onClose={() => setOpen(false)} title="Test dialog">
        <button>Inside</button>
      </Modal>
    </div>
  );
};

describe('modal accessibility', () => {
  it('closes on Escape and returns focus to the trigger', async () => {
    render(<Harness />);
    const trigger = screen.getByRole('button', { name: 'Open dialog' });
    trigger.focus();
    fireEvent.click(trigger);

    const dialog = await screen.findByRole('dialog', { name: 'Test dialog' });
    expect(dialog).toBeTruthy();
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(document.activeElement).toBe(trigger);
  });

  it('traps Tab focus inside the dialog', async () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: 'Open dialog' }));
    const dialog = await screen.findByRole('dialog', { name: 'Test dialog' });

    const inside = screen.getByRole('button', { name: 'Inside' });
    inside.focus();
    expect(dialog.contains(document.activeElement)).toBe(true);

    fireEvent.keyDown(document, { key: 'Tab' });
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));
  });
});
