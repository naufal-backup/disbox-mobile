import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseServiceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

let client;
if (supabaseUrl && supabaseServiceKey) {
  client = createClient(supabaseUrl, supabaseServiceKey);
} else {
  console.error('Missing Supabase environment variables');
}

export const supabase = client;
