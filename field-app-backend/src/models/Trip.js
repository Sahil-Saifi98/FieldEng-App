const mongoose = require('mongoose');

// Individual expense line item within a trip
const tripExpenseSchema = new mongoose.Schema({
  expenseType: {
    type: String,
    required: true,
    enum: ['Hotel / Lodging', 'Air / Train / Bus', 'Daily Allowance', 'Local Conveyance', 'Other Expenses']
  },
  details: { type: String, default: '' },
  travelFrom: { type: String, default: '' },
  travelTo: { type: String, default: '' },
  travelMode: { type: String, default: '' }, // Air / Train / Bus
  daysCount: { type: Number, default: 0 },
  ratePerDay: { type: Number, default: 0 },
  amount: { type: Number, required: true },
  receiptUrl: { type: String, default: null } // Cloudinary URL
});

const tripSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  employeeId: { type: String, required: true },
  employeeName: { type: String, default: '' },
  designation: { type: String, default: '' },

  // Trip header
  stationVisited: { type: String, required: true },
  periodFrom: { type: String, required: true }, // DD-MM-YYYY
  periodTo: { type: String, required: true },
  advanceAmount: { type: Number, default: 0 },

  // Expense line items
  expenses: [tripExpenseSchema],

  // Computed
  totalAmount: { type: Number, default: 0 },
  payableAmount: { type: Number, default: 0 }, // totalAmount - advanceAmount

  status: {
    type: String,
    enum: ['pending', 'approved', 'rejected'],
    default: 'pending'
  },

  // Admin fields
  reviewedBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User', default: null },
  reviewedAt: { type: Date, default: null },
  adminNote: { type: String, default: '' },

  createdAt: { type: Date, default: Date.now }
});

// Indexes for fast admin queries
tripSchema.index({ userId: 1, createdAt: -1 });
tripSchema.index({ employeeId: 1, status: 1 });
tripSchema.index({ status: 1, createdAt: -1 });

module.exports = mongoose.model('Trip', tripSchema);