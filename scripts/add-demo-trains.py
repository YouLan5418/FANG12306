"""Append complete, fictional demo departures without changing existing trains.
Run: python scripts/add-demo-trains.py --apply
Defaults: tomorrow in Asia/Shanghai, seven days, six corridors in both directions.
"""
import argparse, json, subprocess
from datetime import datetime, timedelta, timezone, date
from pathlib import Path

ROUTES = [
    (["北京南", "济南西", "南京南", "上海虹桥"], [0, 90, 230, 300], 55300),
    (["北京西", "郑州东", "武汉", "长沙南", "广州南"], [0, 150, 250, 330, 500], 86200),
    (["上海虹桥", "杭州东", "宁波"], [0, 55, 115], 14400),
    (["广州南", "深圳北"], [0, 45], 7500),
    (["成都东", "重庆北"], [0, 100], 15400),
    (["西安北", "郑州东"], [0, 130], 23900),
]
SQL_BODY = r"""
DO $$
DECLARE
  trip RECORD;
  train_key BIGINT;
  stamp TIMESTAMP;
  arrival_stamp TIMESTAMP;
  departure_stamp TIMESTAMP;
  first_region TEXT;
  last_region TEXT;
  station_key BIGINT;
  region_key TEXT;
  i INT; j INT; n INT;
  seat_spec RECORD;
  fare INT;
  inserted_count INT := 0;
BEGIN
  FOR trip IN SELECT * FROM supplement_plan ORDER BY riding_date, start_hour, train_number LOOP
    n := cardinality(trip.stations);
    stamp := trip.riding_date::timestamp + make_interval(hours => trip.start_hour);
    IF EXISTS (SELECT 1 FROM t_train WHERE train_number=trip.train_number AND departure_time=stamp AND del_flag=0) THEN
      CONTINUE;
    END IF;
    IF EXISTS (SELECT 1 FROM unnest(trip.stations) name WHERE NOT EXISTS (SELECT 1 FROM t_station s WHERE s.name=name AND s.del_flag=0)) THEN
      RAISE EXCEPTION 'Missing station for %', trip.train_number;
    END IF;
    SELECT region INTO first_region FROM t_station WHERE name=trip.stations[1] AND del_flag=0 ORDER BY id LIMIT 1;
    SELECT region INTO last_region FROM t_station WHERE name=trip.stations[n] AND del_flag=0 ORDER BY id LIMIT 1;
    INSERT INTO t_train(train_number,train_type,train_tag,train_brand,start_station,end_station,start_region,end_region,sale_time,sale_status,departure_time,arrival_time)
      VALUES(trip.train_number,0,'DEMO-SUPPLEMENT','复兴号',trip.stations[1],trip.stations[n],first_region,last_region,
             (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Shanghai')-interval '1 day',0,stamp,stamp+make_interval(mins=>trip.offsets[n])) RETURNING id INTO train_key;
    inserted_count := inserted_count+1;
    FOR i IN 1..n LOOP
      SELECT id,region INTO station_key,region_key FROM t_station WHERE name=trip.stations[i] AND del_flag=0 ORDER BY id LIMIT 1;
      arrival_stamp := stamp+make_interval(mins=>trip.offsets[i]);
      departure_stamp := arrival_stamp+CASE WHEN i>1 AND i<n THEN interval '4 minutes' ELSE interval '0 minutes' END;
      INSERT INTO t_train_station(train_id,station_id,sequence,departure,arrival,start_region,end_region,arrival_time,departure_time,stopover_time)
        VALUES(train_key,station_key,i::text,trip.stations[i],trip.stations[i],region_key,region_key,arrival_stamp,departure_stamp,CASE WHEN i>1 AND i<n THEN 4 ELSE 0 END);
    END LOOP;
    INSERT INTO t_carriage(train_id,carriage_number,carriage_type,seat_count) VALUES
      (train_key,'01',2,40),(train_key,'02',2,40),(train_key,'03',1,20),(train_key,'04',0,6);
    FOR i IN 1..n-1 LOOP
      FOR j IN i+1..n LOOP
        INSERT INTO t_train_station_relation(train_id,departure,arrival,start_region,end_region,departure_flag,arrival_flag,departure_time,arrival_time)
          SELECT train_key,trip.stations[i],trip.stations[j],s.region,a.region,CASE WHEN i=1 THEN 1 ELSE 0 END,CASE WHEN j=n THEN 1 ELSE 0 END,
            stamp+make_interval(mins=>trip.offsets[i])+CASE WHEN i>1 THEN interval '4 minutes' ELSE interval '0 minutes' END,
            stamp+make_interval(mins=>trip.offsets[j])
          FROM t_station s, t_station a WHERE s.id=(SELECT station_id FROM t_train_station WHERE train_id=train_key AND sequence=i::text)
            AND a.id=(SELECT station_id FROM t_train_station WHERE train_id=train_key AND sequence=j::text);
        FOR seat_spec IN SELECT * FROM (VALUES(2,1.0),(1,1.6),(0,3.1)) seat_prices(seat_type,multiplier) LOOP
          fare := greatest(500,round(trip.base_price*(trip.offsets[j]-trip.offsets[i])::numeric/trip.offsets[n]*seat_spec.multiplier/100)::int*100);
          INSERT INTO t_train_station_price(train_id,departure,arrival,seat_type,price) VALUES(train_key,trip.stations[i],trip.stations[j],seat_spec.seat_type,fare);
          INSERT INTO t_seat(id,train_id,carriage_number,seat_number,seat_type,start_station,end_station,price,seat_status)
            SELECT nextval('demo_supplement_seat_id_seq'),train_key,c.carriage_number,lpad(row_number::text,2,'0')||letter,
                   seat_spec.seat_type,trip.stations[i],trip.stations[j],fare,0
            FROM (VALUES('01',2,8,'ABCDF'),('02',2,8,'ABCDF'),('03',1,5,'ACDF'),('04',0,2,'ACF')) c(carriage_number,seat_type,row_count,letters),
              LATERAL generate_series(1,c.row_count) row_number,
              LATERAL regexp_split_to_table(c.letters,'') letter WHERE c.seat_type=seat_spec.seat_type;
        END LOOP;
      END LOOP;
    END LOOP;
  END LOOP;
  RAISE NOTICE 'Added % demo departures', inserted_count;
END $$;
COMMIT;
"""

def quote(value): return "'"+str(value).replace("'","''")+"'"
def build(start,days):
    planned=[]
    for day in range(days):
        riding=start+timedelta(days=day)
        for corridor,(stations,offsets,price) in enumerate(ROUTES):
            for direction in range(2):
                names=stations if direction==0 else list(reversed(stations))
                minutes=offsets if direction==0 else [offsets[-1]-v for v in reversed(offsets)]
                for slot,hour in enumerate((8,14)):
                    number="G"+str(9801+corridor*4+direction*2+slot)
                    planned.append("("+",".join([quote(number),quote(riding),str(hour),"ARRAY["+",".join(map(quote,names))+"]","ARRAY["+",".join(map(str,minutes))+"]",str(price)])+")")
    return ("-- Fictional demo trains; schedules and fares are not official railway data.\n"
      "-- Append only; repeat runs skip existing departures with the same train number and timestamp.\n"
      "BEGIN;\nSELECT pg_advisory_xact_lock(hashtext('index12306-demo-supplement'));\n"
      "CREATE SEQUENCE IF NOT EXISTS demo_supplement_seat_id_seq START WITH 870000000000000000;\n"
      "CREATE TEMP TABLE supplement_plan(train_number text,riding_date date,start_hour integer,stations text[],offsets integer[],base_price integer) ON COMMIT DROP;\n"
      "INSERT INTO supplement_plan VALUES\n"+",\n".join(planned)+";\n"+SQL_BODY)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--start-date",type=date.fromisoformat,default=datetime.now(timezone(timedelta(hours=8))).date()+timedelta(days=1))
    parser.add_argument("--days",type=int,default=7)
    parser.add_argument("--apply",action="store_true")
    parser.add_argument("--container",default="postgres")
    args=parser.parse_args()
    if not 1<=args.days<=14: parser.error("days must be between 1 and 14")
    root=Path(__file__).resolve().parents[1]
    output=root/"out"/("demo-trains-"+args.start_date.isoformat()+".sql")
    output.parent.mkdir(exist_ok=True)
    script=build(args.start_date,args.days)
    output.write_text(script,encoding="utf-8")
    print("SQL:",str(output),flush=True)
    if args.apply:
        result=subprocess.run(["docker","exec","-i",args.container,"psql","-U","postgres","-d","index12306","-v","ON_ERROR_STOP=1"],input=script,encoding="utf-8",capture_output=True)
        if result.returncode: raise RuntimeError(result.stderr)
        print(result.stderr.strip(),flush=True)
    print(json.dumps({"start_date":str(args.start_date),"days":args.days,"planned_departures":len(ROUTES)*4*args.days,"physical_seats_per_departure":106,"applied":args.apply}))
if __name__=="__main__": main()
