const Trip = require('../models/Trip');
const PDFDocument = require('pdfkit');
const axios = require('axios');
const fs = require('fs');
const path = require('path');

// ── Helpers ───────────────────────────────────────────────────────

// Map DB expenseType string → section number on the physical form
const SECTION_ORDER = {
  'Air / Train / Bus':   1,
  'Hotel / Lodging':     2,
  'Local Conveyance':    3,
  'Daily Allowance':     4,
  'Other Expenses':      5,
};

async function downloadReceiptToTemp(url, destPath) {
  try {
    const res = await axios({ method: 'GET', url, responseType: 'stream', timeout: 30000 });
    await new Promise((resolve, reject) => {
      const ws = fs.createWriteStream(destPath);
      res.data.pipe(ws);
      ws.on('finish', resolve);
      ws.on('error', reject);
    });
    return true;
  } catch (e) {
    console.error(`Receipt download failed (${url}): ${e.message}`);
    return false;
  }
}

// ── PDF generator — one page per trip, matching TOUR_FORM layout ──

function generateTripPdf(doc, trip, isFirstTrip) {
  const M = 40;          // margin
  const PW = 595 - M * 2; // usable width (A4 portrait = 595pt)
  const col = {           // column x positions for the expense table
    sr:      M,
    details: M + 30,
    from:    M + 220,
    to:      M + 310,
    days:    M + 390,
    amount:  M + 440,
  };

  if (!isFirstTrip) doc.addPage();

  let y = M;

  // ── TITLE ────────────────────────────────────────────────────────
  doc.font('Helvetica-Bold').fontSize(14)
     .text('TRAVELLING EXPENSES', M, y, { width: PW, align: 'center' });
  y += 22;
  doc.moveTo(M, y).lineTo(M + PW, y).lineWidth(1.5).stroke();
  y += 8;

  // ── HEADER INFO TABLE ─────────────────────────────────────────────
  // Row 1: NAME | DESIGNATION
  doc.font('Helvetica-Bold').fontSize(8).text('NAME', M, y);
  doc.font('Helvetica').text(trip.employeeName || '', M + 50, y);
  doc.font('Helvetica-Bold').text('DESIGNATION', M + 280, y);
  doc.font('Helvetica').text(trip.designation || '', M + 360, y);
  y += 16;

  // Row 2: DATE | STATION VISITED
  const submittedDate = trip.createdAt
    ? new Date(trip.createdAt).toLocaleDateString('en-IN')
    : '';
  doc.font('Helvetica-Bold').fontSize(8).text('DATE', M, y);
  doc.font('Helvetica').text(submittedDate, M + 50, y);
  doc.font('Helvetica-Bold').text('STATION VISITED', M + 280, y);
  doc.font('Helvetica').text(trip.stationVisited || '', M + 380, y);
  y += 16;

  // Row 3: PERIOD | NO. OF DAYS
  const period = `${trip.periodFrom || ''} — ${trip.periodTo || ''}`;
  const numDays = trip.expenses.reduce((s, e) => s + (e.daysCount || 0), 0);
  doc.font('Helvetica-Bold').fontSize(8).text('PERIOD', M, y);
  doc.font('Helvetica').text(period, M + 50, y);
  doc.font('Helvetica-Bold').text('NO. OF DAYS', M + 280, y);
  doc.font('Helvetica').text(numDays > 0 ? String(numDays) : '', M + 360, y);
  y += 12;

  doc.moveTo(M, y).lineTo(M + PW, y).lineWidth(0.5).stroke();
  y += 8;

  // ── EXPENSE SECTIONS ──────────────────────────────────────────────
  const sectionDefs = [
    { num: 1, title: 'AIR / TRAIN / BUS FARE', key: 'Air / Train / Bus',
      cols: ['FROM', 'TO', '', 'AMOUNT'] },
    { num: 2, title: 'HOTEL BOOKING / LODGING CHARGE', key: 'Hotel / Lodging',
      cols: ['PLACE', 'NO. OF DAYS', '', 'AMOUNT'] },
    { num: 3, title: 'LOCAL CONVEYANCE', key: 'Local Conveyance',
      cols: ['FROM', 'TO', '', 'AMOUNT'] },
    { num: 4, title: 'DAILY ALLOWANCES', key: 'Daily Allowance',
      cols: ['DETAILS', '', '', 'AMOUNT'] },
    { num: 5, title: 'OTHER EXPENSES', key: 'Other Expenses',
      cols: ['DETAILS', '', '', 'AMOUNT'] },
  ];

  for (const sec of sectionDefs) {
    const items = trip.expenses.filter(e => e.expenseType === sec.key);

    // Section header row
    doc.font('Helvetica-Bold').fontSize(8)
       .text(`${sec.num}.`, col.sr, y, { width: 25 })
       .text(sec.title, col.details, y, { width: 180 });

    // Column sub-headers (right side)
    if (sec.cols[0]) doc.fontSize(7).text(sec.cols[0], col.from, y, { width: 80, align: 'left' });
    if (sec.cols[1]) doc.fontSize(7).text(sec.cols[1], col.to,   y, { width: 80, align: 'left' });
    doc.fontSize(7).text('AMOUNT', col.amount, y, { width: 70, align: 'right' });
    y += 13;

    doc.moveTo(M, y).lineTo(M + PW, y).lineWidth(0.3).stroke('#CCCCCC');
    y += 4;

    if (items.length === 0) {
      // Empty row placeholder
      doc.font('Helvetica').fontSize(7).fillColor('#AAAAAA')
         .text('—', col.details, y, { width: 180 })
         .fillColor('black');
      y += 13;
    } else {
      for (const item of items) {
        const amt = `₹${Number(item.amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;

        if (sec.key === 'Air / Train / Bus' || sec.key === 'Local Conveyance') {
          doc.font('Helvetica').fontSize(8)
             .text(item.details || '', col.details, y, { width: 170 })
             .text(item.travelFrom || '', col.from,    y, { width: 80 })
             .text(item.travelTo   || '', col.to,      y, { width: 70 })
             .text(amt,                  col.amount,   y, { width: 70, align: 'right' });
        } else if (sec.key === 'Hotel / Lodging') {
          doc.font('Helvetica').fontSize(8)
             .text(item.details || item.travelFrom || '', col.details, y, { width: 170 })
             .text(item.daysCount > 0 ? String(item.daysCount) : '', col.from, y, { width: 80 })
             .text(amt, col.amount, y, { width: 70, align: 'right' });
        } else {
          doc.font('Helvetica').fontSize(8)
             .text(item.details || '', col.details, y, { width: 260 })
             .text(amt,               col.amount,   y, { width: 70, align: 'right' });
        }
        y += 13;
      }
    }

    doc.moveTo(M, y).lineTo(M + PW, y).lineWidth(0.3).stroke('#CCCCCC');
    y += 6;
  }

  // ── FOOTER TOTALS ─────────────────────────────────────────────────
  y += 4;
  doc.moveTo(M, y).lineTo(M + PW, y).lineWidth(1).stroke();
  y += 8;

  const fmtAmt = (n) =>
    `₹${Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;

  // Ticker amount
  doc.font('Helvetica-Bold').fontSize(8)
     .text('TICKER AMOUNT PAID BY THE COMPANY', M, y, { width: 300 });
  doc.font('Helvetica').text(fmtAmt(trip.totalAmount), M + 310, y, { width: 160, align: 'right' });
  y += 14;

  // Advance / Payable row
  doc.font('Helvetica-Bold').fontSize(8).text('ADVANCE / IMPREST', M, y);
  doc.font('Helvetica').text(fmtAmt(trip.advanceAmount), M + 120, y, { width: 100 });
  doc.font('Helvetica-Bold').text('AMOUNT PAYABLE / REFUNDABLE', M + 240, y);
  doc.font('Helvetica').text(fmtAmt(trip.payableAmount), M + 420, y, { width: 95, align: 'right' });
  y += 6;

  doc.moveTo(M, y).lineTo(M + PW, y).lineWidth(0.5).stroke();
  y += 20;

  // Status badge
  const statusColor = trip.status === 'approved' ? '#2E7D32'
    : trip.status === 'rejected' ? '#C62828' : '#E65100';
  doc.font('Helvetica-Bold').fontSize(8).fillColor(statusColor)
     .text(`STATUS: ${(trip.status || 'PENDING').toUpperCase()}`, M, y);
  if (trip.adminNote) {
    doc.font('Helvetica').fontSize(7).fillColor('#444444')
       .text(`Note: ${trip.adminNote}`, M + 130, y, { width: 280 });
  }
  doc.fillColor('black');
  y += 20;

  // ── SIGNATURE LINE ────────────────────────────────────────────────
  doc.moveTo(M, y).lineTo(M + PW, y).lineWidth(0.5).stroke();
  y += 8;
  const sigW = PW / 3;
  doc.font('Helvetica-Bold').fontSize(8)
     .text('SIGNATURE', M,              y, { width: sigW, align: 'center' })
     .text('ACCOUNTANT', M + sigW,      y, { width: sigW, align: 'center' })
     .text('MANAGER',    M + sigW * 2,  y, { width: sigW, align: 'center' });

  return doc;
}

// ── Controller: export all trips for a user as PDF ────────────────

exports.exportUserExpensesPdf = async (req, res) => {
  const { userId } = req.params;
  const { startDate, endDate } = req.query;

  try {
    const User = require('../models/User');
    const user = await User.findById(userId).select('-password');
    if (!user) return res.status(404).json({ success: false, message: 'User not found' });

    let query = { userId };
    if (startDate && endDate) {
      // createdAt range
      query.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(new Date(endDate).setHours(23, 59, 59))
      };
    }

    const trips = await Trip.find(query).sort({ createdAt: -1 });

    if (trips.length === 0) {
      return res.status(404).json({
        success: false,
        message: 'No expense trips found for the selected date range'
      });
    }

    const doc = new PDFDocument({ margin: 40, size: 'A4', bufferPages: true });
    const filename = `${user.employeeId}_expenses_${Date.now()}.pdf`;

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    doc.pipe(res);

    trips.forEach((trip, i) => generateTripPdf(doc, trip, i === 0));

    doc.end();
    console.log(`✅ Expense PDF exported: ${filename} (${trips.length} trips)`);
  } catch (error) {
    console.error('❌ Expense PDF export error:', error);
    if (!res.headersSent) {
      res.status(500).json({ success: false, message: error.message });
    }
  }
};

// ── Controller: export all expense trips (all users) as PDF ───────

exports.exportAllExpensesPdf = async (req, res) => {
  const { startDate, endDate } = req.query;

  try {
    let query = {};
    if (startDate && endDate) {
      query.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(new Date(endDate).setHours(23, 59, 59))
      };
    }

    const trips = await Trip.find(query).sort({ employeeId: 1, createdAt: -1 });

    if (trips.length === 0) {
      return res.status(404).json({
        success: false,
        message: 'No expense trips found for the selected date range'
      });
    }

    const doc = new PDFDocument({ margin: 40, size: 'A4', bufferPages: true });
    const filename = `all_expenses_${Date.now()}.pdf`;

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    doc.pipe(res);

    trips.forEach((trip, i) => generateTripPdf(doc, trip, i === 0));

    doc.end();
    console.log(`✅ All-expenses PDF exported: ${filename} (${trips.length} trips)`);
  } catch (error) {
    console.error('❌ All expenses PDF export error:', error);
    if (!res.headersSent) {
      res.status(500).json({ success: false, message: error.message });
    }
  }
};

// ── Controller: export all expense trips as CSV ───────────────────

exports.exportAllExpensesCSV = async (req, res) => {
  const { startDate, endDate } = req.query;

  try {
    let query = {};
    if (startDate && endDate) {
      query.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(new Date(endDate).setHours(23, 59, 59))
      };
    }

    const trips = await Trip.find(query).sort({ employeeId: 1, createdAt: -1 });

    let csv = 'Employee ID,Employee Name,Designation,Station Visited,Period From,Period To,' +
              'Expense Type,Details,Travel From,Travel To,Days,Rate/Day,Line Amount,' +
              'Trip Total,Advance,Payable,Status,Receipt URL,Submitted On\n';

    trips.forEach(trip => {
      if (trip.expenses.length === 0) {
        csv += `"${trip.employeeId}","${trip.employeeName}","${trip.designation}",` +
               `"${trip.stationVisited}","${trip.periodFrom}","${trip.periodTo}",` +
               `,,,,,,,"${trip.totalAmount}","${trip.advanceAmount}","${trip.payableAmount}",` +
               `"${trip.status}",,"${new Date(trip.createdAt).toLocaleDateString('en-IN')}"\n`;
      } else {
        trip.expenses.forEach((exp, i) => {
          csv += `"${trip.employeeId}","${trip.employeeName}","${trip.designation}",` +
                 `"${trip.stationVisited}","${trip.periodFrom}","${trip.periodTo}",` +
                 `"${exp.expenseType}","${exp.details}","${exp.travelFrom}","${exp.travelTo}",` +
                 `${exp.daysCount},${exp.ratePerDay},${exp.amount},` +
                 `${i === 0 ? trip.totalAmount : ''},${i === 0 ? trip.advanceAmount : ''},` +
                 `${i === 0 ? trip.payableAmount : ''},"${i === 0 ? trip.status : ''}",` +
                 `"${exp.receiptUrl || ''}","${i === 0 ? new Date(trip.createdAt).toLocaleDateString('en-IN') : ''}"\n`;
        });
      }
    });

    const filename = `all_expenses_${Date.now()}.csv`;
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.status(200).send(csv);
    console.log(`✅ All-expenses CSV exported: ${filename}`);
  } catch (error) {
    console.error('❌ Expenses CSV error:', error);
    res.status(500).json({ success: false, message: error.message });
  }
};

// ── Helpers exported for use in adminController ZIP exports ───────

exports.generateTripPdf = generateTripPdf;
exports.downloadReceiptToTemp = downloadReceiptToTemp;