import { filenameFrom } from './file-saver';

describe('filenameFrom', () => {
  it('reads the file name sent by the backend', () => {
    expect(filenameFrom('attachment; filename="inventario-20261023-0938.pdf"')).toBe(
      'inventario-20261023-0938.pdf',
    );
  });

  it('decodes RFC 5987 names', () => {
    expect(filenameFrom("attachment; filename*=UTF-8''inventario%20norte.xlsx")).toBe(
      'inventario norte.xlsx',
    );
  });

  it('returns undefined without the header', () => {
    expect(filenameFrom(null)).toBeUndefined();
  });
});
